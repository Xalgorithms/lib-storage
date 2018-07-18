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
package org.xalgorithms.storage.bson

import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.exceptions._
import org.scalatest.concurrent.ScalaFutures

import collection.JavaConverters._
import org.mongodb.scala._
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonInt32, BsonString }
import play.api.libs.json._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.util.{ Success, Failure }

import org.xalgorithms.storage.data.{ Logger, Mongo, MongoActions }

// WARNING: This spec REQUIRES a working MongoDB running @localhost:27017
class MongoSpec extends FlatSpec
    with Matchers with MockFactory with ScalaFutures with AppendedClues with BeforeAndAfterEach {
  import scala.concurrent.ExecutionContext.Implicits.global

  class NullLogger extends Logger {
    def debug(m: String) = { }
    def error(m: String) = { }
    def info(m: String)  = { }
  }

  val log = new NullLogger
  val mongo = new Mongo(log, Some("mongodb://localhost:27017"))

  def generate_json_document(i: Int) = Json.obj(
    s"a${i}" -> i.toString,
    s"b${i}" -> (i + 1).toString,
    s"c${i}" -> (i + 2).toString,
    s"d${i}" -> (i + 3).toString
  )

  override def afterEach() = {
    Await.ready(mongo.drop_all_collections(), 20.seconds)
  }

  "Mongo" should "store documents" in {
    val docs = (0 to 5).map { i =>
      // we use Json.obj and convert it to a BsonDocument b/c the
      // Json.obj syntax is much more expressive
      BsonDocument(generate_json_document(i).toString)
    }
    val futs = Future.sequence(docs.map { doc =>
      mongo.store(new MongoActions.StoreDocument(doc)).map { id => (id, doc) }
    })

    whenReady(futs) { tups =>
      tups.size shouldEqual(docs.size)
      val find_futs = Future.sequence(
        tups.map { tup =>
          mongo.find_one(MongoActions.FindDocumentById(tup._1)).map { found => (found, Document(tup._2), tup._1) }
        }
      )

      whenReady(find_futs) { doc_tups =>
        doc_tups.size shouldEqual(docs.size)
        doc_tups.foreach { tup =>
          tup._1 match {
            case Some(ac_doc) => {
              ac_doc.get("_id") shouldBe None
              ac_doc.get("public_id") shouldEqual(Some(BsonString(tup._3)))
              tup._2.foreach { case (k, v) =>
                ac_doc.get("content") match {
                  case Some(ac_content) => {
                    ac_content shouldBe a [BsonDocument]
                    val ac_v = ac_content.asInstanceOf[BsonDocument].get(k)
                    ac_v shouldEqual(v) withClue s"in doc(${tup._3}), expected ${k}==${v} but got ${ac_v}"
                  }
                  case None => fail(s"expected document to contain 'content' (id=${tup._3})")
                }
              }
            }

            case None => fail(s"expected a document to exist (id=${tup._3})")
          }
        }
      }
    }
  }

  it should "store executions" in {
    // generates a Seq of Tuples (rule_id, ctx)
    val execs = (0 to 5).map { i =>
      (s"rule_id_${i}", generate_json_document(i))
    }

    // yields a Future which yields a Seq(stored_id, original_tuple)
    val futs = Future.sequence(execs.map { tup =>
      mongo.store(
        new MongoActions.StoreExecution(tup._1, tup._2)
      ).map { id => (id, tup) }
    })

    whenReady(futs) { tups =>
      tups.size shouldEqual(execs.size)
      // yields a Future Seq of (document from mongo, original id, original Tuple)
      val find_futs = Future.sequence(
        tups.map { tup =>
          mongo.find_one(MongoActions.FindExecutionById(tup._1)).map { found => (found, tup._1, tup._2) }
        }
      )

      whenReady(find_futs) { results_tups =>
        results_tups.size shouldEqual(execs.size)
        results_tups.foreach { result_tup =>
          result_tup._1 match {
            case Some(res_doc) => {
              res_doc.get("_id") shouldBe None
              res_doc.get("request_id") shouldEqual(Some(BsonString(result_tup._2)))
              res_doc.get("rule_id") shouldEqual(Some(BsonString(result_tup._3._1)))
              res_doc.get("context") shouldEqual(Some(BsonDocument(result_tup._3._2.toString)))
            }

            case None => fail(s"expected a document to exist (id=${result_tup._2})")
          }
        }
      }
    }
  }

  it should "store test runs" in {
    // generates a Seq of Tuples (rule_id, ctx)
    val execs = (0 to 5).map { i =>
      (s"rule_id_${i}", generate_json_document(i))
    }

    // yields a Future which yields a Seq(stored_id, original_tuple)
    val futs = Future.sequence(execs.map { tup =>
      mongo.store(
        new MongoActions.StoreTestRun(tup._1, tup._2)
      ).map { id => (id, tup) }
    })

    whenReady(futs) { tups =>
      tups.size shouldEqual(execs.size)
      // yields a Future Seq of (document from mongo, original id, original Tuple)
      val find_futs = Future.sequence(
        tups.map { tup =>
          mongo.find_one(MongoActions.FindTestRunById(tup._1)).map { found => (found, tup._1, tup._2) }
        }
      )

      whenReady(find_futs) { results_tups =>
        results_tups.size shouldEqual(execs.size)
        results_tups.foreach { result_tup =>
          result_tup._1 match {
            case Some(res_doc) => {
              res_doc.get("_id") shouldBe None
              res_doc.get("request_id") shouldEqual(Some(BsonString(result_tup._2)))
              res_doc.get("rule_id") shouldEqual(Some(BsonString(result_tup._3._1)))
              res_doc.get("context") shouldEqual(Some(BsonDocument(result_tup._3._2.toString)))
            }

            case None => fail(s"expected a document to exist (id=${result_tup._2})")
          }
        }
      }
    }
  }

  it should "store traces" in {
    // generates a Seq of Tuples (rule_id, ctx)
    val ids = (0 to 5).map { i => s"request_id_${i}" }

    // yields a Future which yields a Seq(stored_id, original_tuple)
    val futs = Future.sequence(ids.map { req_id =>
      mongo.store(
        new MongoActions.StoreTrace(req_id)
      ).map { id => (id, req_id) }
    })

    whenReady(futs) { tups =>
      tups.size shouldEqual(ids.size)
      // yields a Future Seq of (document from mongo, original id, original Tuple)
      val find_futs = Future.sequence(
        tups.map { tup =>
          mongo.find_one(MongoActions.FindTraceById(tup._1)).map { found => (found, tup._1, tup._2) }
        }
      )

      whenReady(find_futs) { results_tups =>
        results_tups.size shouldEqual(ids.size)
        results_tups.foreach { result_tup =>
          result_tup._1 match {
            case Some(res_doc) => {
              res_doc.get("_id") shouldBe None
              res_doc.get("public_id") shouldEqual(Some(BsonString(result_tup._2)))
              res_doc.get("request_id") shouldEqual(Some(BsonString(result_tup._3)))
              res_doc.get("steps") shouldEqual(Some(BsonArray()))
            }

            case None => fail(s"expected a document to exist (id=${result_tup._2})")
          }
        }
      }

      val find_many_futs = Future.sequence(
        tups.map { tup =>
          mongo.find_many(MongoActions.FindManyTracesByRequestId(tup._2)).map { found =>
            (found, tup._1, tup._2)
          }
        }
      )

      whenReady(find_many_futs) { results_tups =>
        results_tups.size shouldEqual(ids.size)
        results_tups.foreach { result_tup =>
          result_tup._1 match {
            case Some(seq) => {
              seq.size shouldEqual(1)
              seq.head.get("_id") shouldBe None
              seq.head.get("public_id") shouldEqual(Some(BsonString(result_tup._2)))
              seq.head.get("request_id") shouldEqual(Some(BsonString(result_tup._3)))
              seq.head.get("steps") shouldEqual(Some(BsonArray()))
            }
            case None => fail(s"expected documents to exist (id=${result_tup._2})")
          }
        }
      }
    }
  }

  it should "add contexts to traces" in {
    // store a trace
    val request_id = "request_add_context"
    whenReady(mongo.store(MongoActions.StoreTrace(request_id))) { trace_public_id =>
      val updates = (0 to 5).map { i =>
        (s"phase_${i}", i, generate_json_document(i))
      }

      val update_futs = Future.sequence(updates.map { case (phase, index, ctx) =>
        mongo.update_one(
          MongoActions.AddContext(request_id, phase, index, ctx)
        ).map { changes => (phase, index, ctx, changes) }
      })
      whenReady(update_futs) { results =>
        results.size shouldEqual(updates.size)
        results.foreach { case (phase, index, ctx, changes) =>
          changes._1 shouldEqual(1)
          changes._2 shouldEqual(1)
        }
        whenReady(mongo.find_one(MongoActions.FindTraceById(trace_public_id))) { doc =>
          val doc_steps = doc.get("steps")
          doc_steps should not be null
          doc_steps shouldBe a [BsonArray]

          val steps = doc_steps.asInstanceOf[BsonArray].getValues().asScala
          steps.size shouldEqual(updates.size)
          steps.foreach { step => step shouldBe a [BsonDocument] }

          updates.map { case (phase, index, ctx) =>
            (BsonString(phase), BsonInt32(index), BsonDocument(ctx.toString))
          }.foreach { case (ex_phase, ex_index, ex_ctx) =>
            steps.filter { step_v =>
              val step = step_v.asInstanceOf[BsonDocument]
              val ac_phase = step.get("phase")
              val ac_index = step.get("index")
              val ac_ctx = step.get("context")

              ac_phase == ex_phase && ac_index == ex_index && ac_ctx == ex_ctx
            }.size shouldEqual(1) withClue(s"could not locate the step (phase=${ex_phase}; index=${ex_index})")
          }
        }
      }
    }
  }
}
