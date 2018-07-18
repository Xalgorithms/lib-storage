// Copyright (C) 2018 Don Kelly <karfai@gmail.com>

// This file is part of Interlibr, a functional component of an
// Internet of Rules (IoR).

// ACKNOWLEDGEMENTS
// Funds: Xalgorithms Foundation
// Collaborators: Don Kelly, Joseph Potvin and Bill Olders.

// This program is free software: you can redistribute it and/or
// modify it under the terms of the GNU Affero General Public License
// as published by the Free Software Foundation, either version 3 of
// the License, or (at your option) any later version.

// This program is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Affero General Public License for more details.

// You should have received a copy of the GNU Affero General Public
// License along with this program. If not, see
// <http://www.gnu.org/licenses/>.
package org.xalgorithms.storage.data

import com.mongodb.client.result.UpdateResult
import java.util.UUID.randomUUID
import org.mongodb.scala._
import org.mongodb.scala.bson.{ BsonArray, BsonDocument }
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import play.api.libs.json._
import scala.concurrent.{ Future, Promise }

// TODO: this should be provided, not assumed
import scala.concurrent.ExecutionContext.Implicits.global

object MongoActions {
  abstract class Store(val collection_name: String) {
    val public_id = randomUUID.toString()

    def document : Document
  }

  case class StoreDocument(doc: BsonDocument) extends Store("documents") {
    def this(doc: JsObject) = this(BsonDocument(doc.toString()))

    def document: Document = {
      Document("public_id" -> public_id, "content" -> doc)
    }
  }

  case class StoreExecution(rule_id: String, ctx: BsonDocument) extends Store("executions") {
    def this(rule_id: String, ctx: JsObject) = this(rule_id, BsonDocument(ctx.toString()))

    def document: Document = {
      Document("rule_id" -> rule_id, "request_id" -> public_id, "context" -> ctx)
    }
  }

  case class StoreTestRun(rule_id: String, ctx: BsonDocument) extends Store("test-runs") {
    def this(rule_id: String, ctx: JsObject) = this(rule_id, BsonDocument(ctx.toString()))

    def document: Document = {
      Document("rule_id" -> rule_id, "request_id" -> public_id, "context" -> ctx)
    }
  }

  case class StoreTrace(request_id: String) extends Store("traces") {
    def document: Document = Document(
      "public_id"  -> public_id,
      "request_id" -> request_id,
      "steps" -> BsonArray()
    )
  }

  abstract class Update {
    def collection: String
    def condition: Unit
    def update: Bson
  }

  case class AddContext(
    public_id: String, phase: String, index: Int, ctx: JsValue
  ) extends Update {
    def collection = "traces"
    def condition = equal("public_id", public_id)
    def update = push("steps", Document(ctx.toString))
  }

  abstract class Find(val cn: String) {
    def apply(coll: MongoCollection[Document]): FindObservable[Document]
  }

  case class FindByKey(collection_name: String, key: String, value: String) extends Find(collection_name) {
    def apply(coll: MongoCollection[Document]): FindObservable[Document] = coll.find(equal(key, value))
  }

  object FindManyTracesByRequestId {
    def apply(id: String) = FindByKey("traces", "request_id", id)
  }

  object FindDocumentById {
    def apply(id: String) = FindByKey("documents", "public_id", id)
  }

  object FindTraceById {
    def apply(id: String) = FindByKey("traces", "public_id", id)
  }

  object FindExecutionById {
    def apply(id: String) = FindByKey("executions", "request_id", id)
  }

  object FindTestRunById {
    def apply(id: String) = FindByKey("test-runs", "request_id", id)
  }

  object FindRuleById {
    def apply(id: String) = FindByKey("rules", "public_id", id)
  }

  object FindDocumentByReference {
    def apply(cn: String, t: String, ref: String): Find = {
      val k = play.api.libs.Codecs.sha1(s"${t}(${ref})".getBytes)
      FindByKey(cn, "public_id", k)
    }
  }

  object FindTableByReference {
    def apply(ref: String): Find = {
      FindDocumentByReference("tables", "T", ref)
    }

    def apply(pkg: String, id: String, version: String): Find = {
      apply(s"${pkg}:${id}:${version}")
    }
  }

  object FindRuleByReference {
    def apply(ref: String): Find = {
      FindDocumentByReference("rules", "R", ref)
    }
  }
}

abstract class Logger {
  def debug(m: String)
  def error(m: String)
  def info(m: String)
}

class Mongo(log: Logger, url: Option[String] = None) {
  val cl = MongoClient(url.getOrElse("mongodb://mongo:27017/"))
  val db = cl.getDatabase("xadf")

  class PromiseObserver[T](pr: Promise[Option[T]], op_name: String) extends Observer[T] {
    private var result: Option[T] = None

    override def onComplete(): Unit = {
      log.debug(s"observed completed (name=${op_name})")
      pr.success(result)
    }

    override def onNext(t: T): Unit = {
      log.debug(s"observed next (name=${op_name})")
      result = Some(refine(t))
    }

    override def onError(th: Throwable): Unit = {
      log.error(s"failed operation (name=${op_name})")
      println(th)
      pr.failure(th)
    }

    def refine(t: T): T = t
  }

  trait Redact {
    private val NON_PUBLIC_KEYS = Set("_id")

    def redact(doc: Document): Document = {
      doc.filterKeys { k => !NON_PUBLIC_KEYS(k) }
    }

    def redact(docs: Seq[Document]): Seq[Document] = docs.map(redact(_))
  }
  

  class PublicDocumentPromiseObserver(
    pr: Promise[Option[Document]], op_name: String
  ) extends PromiseObserver(pr, op_name) with Redact {
    override def refine(doc: Document): Document = redact(doc)
  }

  class PublicDocumentsPromiseObserver(
    pr: Promise[Option[Seq[Document]]], op_name: String
  ) extends PromiseObserver(pr, op_name) with Redact {
    override def refine(docs: Seq[Document]): Seq[Document] = redact(docs)
  }

  class DirectPromiseObserver[T](pr: Promise[T], op_name: String) extends Observer[T] {
    private var result: Option[T] = None

    override def onComplete(): Unit = {
      log.debug(s"observed completed (name=${op_name})")
      result match {
        case Some(t) => pr.success(t)
        case None    => pr.failure(new Exception("received nothing"))
      }
    }

    override def onNext(t: T): Unit = {
      log.debug(s"observed next (name=${op_name})")
      result = Some(t)
    }

    override def onError(th: Throwable): Unit = {
      log.error(s"failed operation (name=${op_name})")
      println(th)
      pr.failure(th)
    }
  }

  def find_one(op: MongoActions.Find): Future[Option[Document]] = {
    val pr = Promise[Option[Document]]()
    op.apply(db.getCollection(op.cn)).first().subscribe(
      new PublicDocumentPromiseObserver(pr, "find_one")
    )
    pr.future
  }

  def find_one_bson(op: MongoActions.Find): Future[Option[BsonDocument]] = find_one(op).map { opt_doc =>
    opt_doc.map(_.toBsonDocument)
  }

  def find_many(op: MongoActions.Find): Future[Option[Seq[Document]]] = {
    val pr = Promise[Option[Seq[Document]]]
    val coll: Unit = db.getCollection(op.cn)
    op.apply(db.getCollection(op.cn)).collect().subscribe(
      new PublicDocumentsPromiseObserver(pr, "find_many")
    )
    pr.future
  }

  def find_many_bson(
    op: MongoActions.Find
  ): Future[Option[Seq[BsonDocument]]] = find_many(op).map { opt_seq =>
    opt_seq.map { seq => seq.map(_.toBsonDocument) }
  }

  def store(op: MongoActions.Store): Future[String] = {
    val pr = Promise[String]()
    db.getCollection(op.collection_name).insertOne(op.document).subscribe(new Observer[Completed] {
      override def onComplete(): Unit = {
        log.debug(s"insert completed")
      }

      override def onNext(res: Completed): Unit = {
        log.debug(s"insert next")
        pr.success(op.public_id)
      }

      override def onError(th: Throwable): Unit = {
        log.error(s"failed insert, trigging promise")
        pr.failure(th)
      }
    })

    pr.future
  }

  def update(op: MongoActions.Update): Future[Unit] = {
    val pr = Promise[Unit]()
    op match {
      case MongoActions.AddContext(request_id, phase, index, ctx) => {
        val doc = Document(
          "index" -> index,
          "phase" -> phase,
          "context" -> Document(ctx.toString))
        db.getCollection("traces").updateOne(
          equal("request_id", request_id),
          push("steps", doc)
        ).subscribe((update: UpdateResult) => {
          log.info(s"updated (request_id=${request_id}; matched=${update.getMatchedCount}; modified=${update.getModifiedCount})")
          pr.success(None)
        })
      }
    }

    pr.future
  }

  def drop_all_collections(): Future[Int] = {
    val cns = Seq(
      "documents",
      "executions",
      "test-runs",
      "traces"
    )
    Future.sequence(cns.map { cn =>
      val pr = Promise[Completed]
      db.getCollection(cn).drop().subscribe(new DirectPromiseObserver(pr, "drop_all_collections"))
      pr.future
    }).map(_.size)
  }
}
