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

import org.mongodb.scala._
import org.mongodb.scala.bson.{ BsonDocument }
import play.api.libs.json._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.util.{ Success, Failure }

import org.xalgorithms.storage.data.{ Logger, Mongo, MongoActions }

// WARNING: This spec REQUIRES a working MongoDB running @localhost:27017
class MongoSpec extends FlatSpec
    with Matchers with MockFactory with ScalaFutures with AppendedClues {
  import scala.concurrent.ExecutionContext.Implicits.global

  class NullLogger extends Logger {
    def debug(m: String) = { }
    def error(m: String) = { }
    def info(m: String)  = { }
  }

  val log = new NullLogger
  val mongo = new Mongo(log, Some("mongodb://localhost:27017"))

  def generate_document(i: Int) = {
    // we use Json.obj and convert it to a BsonDocument b/c the
    // Json.obj syntax is much more expressive
    BsonDocument(
      Json.obj(
        s"a${i}" -> i.toString,
        s"b${i}" -> (i + 1).toString,
        s"c${i}" -> (i + 2).toString,
        s"d${i}" -> (i + 3).toString
      ).toString
    )
  }

  "Mongo" should "store documents" in {
    val docs = (0 to 5).map(generate_document(_))
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
}
