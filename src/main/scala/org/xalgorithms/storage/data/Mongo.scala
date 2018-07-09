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
import play.api.{ Logger => PlayLogger }
import play.api.libs.json._
import scala.concurrent.{ Future, Promise }

import scala.concurrent.ExecutionContext.Implicits.global

object MongoActions {
  abstract class Store() {
    def document : Document
    def id: String
  }

  case class StoreDocument(doc: BsonDocument) extends Store {
    private val public_id = randomUUID.toString()

    def this(doc: JsObject) = this(BsonDocument(doc.toString()))

    def document: Document = {
      Document("public_id" -> public_id, "content" -> doc)
    }

    def id = public_id
  }

  case class StoreExecution(rule_id: String, ctx: BsonDocument) extends Store {
    private val request_id = randomUUID.toString()

    def this(rule_id: String, ctx: JsObject) = this(rule_id, BsonDocument(ctx.toString()))

    def document: Document = {
      Document("rule_id" -> rule_id, "request_id" -> request_id, "context" -> ctx)
    }

    def id = request_id
  }

  case class StoreTestRun(rule_id: String, ctx: BsonDocument) extends Store {
    private val request_id = randomUUID.toString()

    def document: Document = {
      Document("rule_id" -> rule_id, "request_id" -> request_id, "context" -> ctx)
    }

    def id = request_id
  }

  case class StoreTrace(request_id: String) extends Store {
    private val public_id = randomUUID.toString()

    def document: Document = Document(
      "public_id"  -> public_id,
      "request_id" -> request_id,
      "steps" -> BsonArray()
    )

    def id = public_id
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

  abstract class FindMany()
  case class FindManyDocuments() extends FindMany
  case class FindManyTracesByRequestId(request_id: String) extends FindMany

  class FindOne()
  case class FindByKey(cn: String, key: String, value: String) extends FindOne

  object FindDocumentById {
    def apply(id: String) = FindByKey("documents", "public_id", id)
  }

  object FindTraceById {
    def apply(id: String) = FindByKey("traces", "public_id", id)
  }

  object FindExecutionById {
    def apply(id: String) = FindByKey("executions", "request_id", id)
  }

  object FindRuleById {
    def apply(id: String) = FindByKey("rules", "public_id", id)
  }

  object FindDocumentByReference {
    def apply(cn: String, t: String, ref: String): FindOne = {
      val k = play.api.libs.Codecs.sha1(s"${t}(${ref})".getBytes)
      FindByKey(cn, "public_id", k)
    }
  }

  object FindTableByReference {
    def apply(ref: String): FindOne = {
      FindDocumentByReference("tables", "T", ref)
    }

    def apply(pkg: String, id: String, version: String): FindOne = {
      apply(s"${pkg}:${id}:${version}")
    }
  }

  object FindRuleByReference {
    def apply(ref: String): FindOne = {
      FindDocumentByReference("rules", "R", ref)
    }
  }
}

abstract class Logger {
  def debug(m: String)
  def error(m: String)
  def info(m: String)
}

class Mongo(log: Logger) {
  val url = sys.env.get("MONGO_URL").getOrElse("mongodb://mongo:27017/")
  val cl = MongoClient(url)
  val db = cl.getDatabase("xadf")

  def find_one(op: MongoActions.FindOne): Future[Document] = {
    val pr = Promise[Document]()

    op match {
      case MongoActions.FindByKey(cn, key, value) => {
        db.getCollection(cn).find(equal(key, value)).first().subscribe(
          (doc: Document) => pr.success(censor(doc))
        )
      }
    }

    pr.future
  }

  def find_one_bson(op: MongoActions.FindOne): Future[BsonDocument] = {
    find_one(op).map(_.toBsonDocument)
  }

  val NON_PUBLIC_KEYS = Set("_id")

  def censor(doc: Document): Document = {
    doc.filterKeys { k => !NON_PUBLIC_KEYS(k) }
  }

  def censor(docs: Seq[Document]): Seq[Document] = docs.map(censor(_))

  def find_many(op: MongoActions.FindMany): Future[Seq[Document]] = {
    val pr = Promise[Seq[Document]]

    op match {
      case MongoActions.FindManyDocuments() => {
        db.getCollection("documents").find().collect().subscribe(
          (docs: Seq[Document]) => {
            pr.success(censor(docs))
          }
        )
      }
      case MongoActions.FindManyTracesByRequestId(request_id) => {
        db.getCollection("traces").find(equal("request_id", request_id)).collect().subscribe(
          (docs: Seq[Document]) => {
            pr.success(censor(docs))
          }
        )
      }
    }

    pr.future
  }

  def store(op: MongoActions.Store): Future[String] = {
    val pr = Promise[String]()
    val cn = op match {
      case MongoActions.StoreDocument(_) => "documents"
      case MongoActions.StoreExecution(_, _)  => "executions"
      case MongoActions.StoreTestRun(_, _)  => "test-runs"
      case MongoActions.StoreTrace(_)  => "traces"
    }

    db.getCollection(cn).insertOne(op.document).subscribe(new Observer[Completed] {
      override def onComplete(): Unit = {
        PlayLogger.debug(s"insert completed")
      }

      override def onNext(res: Completed): Unit = {
        PlayLogger.debug(s"insert next")
        pr.success(op.id)
      }

      override def onError(th: Throwable): Unit = {
        PlayLogger.error(s"failed insert, trigging promise")
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
}
