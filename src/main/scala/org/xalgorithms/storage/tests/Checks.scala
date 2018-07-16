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
package org.xalgorithms.storage.tests

// NOTE: This is a rudiamentary test program for the Mongo class. The
// queries that are run may or may not be in the database.

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await }
import scala.util.{ Success, Failure }

import org.xalgorithms.storage.data.{ Logger, Mongo, MongoActions }

object Checks extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  
  class ConsoleLogger extends Logger {
    def debug(m: String) = println(s"# ${m}")
    def error(m: String) = println(s"! ${m}")
    def info(m: String)  = println(s"# ${m}")
  }

  val log = new ConsoleLogger

  log.info("connecting to mongo")
  val mongo = new Mongo(log, Some("mongodb://localhost:27017"))

  basics
  many

  def basics {
    log.info("running basic queries")
    val results = for {
      res0 <- mongo.find_one(MongoActions.FindTableByReference("test:origin:0.0.0"))
      res1 <- mongo.find_one(MongoActions.FindTableByReference("test:joiner:0.0.0"))
      res2 <- mongo.find_one(MongoActions.FindRuleByReference("dune:assemble:99.99.99"))
      res3 <- mongo.find_one(MongoActions.FindRuleByReference("dune:join:99.99.99"))
    } yield (res0, res1, res2, res3)

    results.onComplete {
      case Success((table0, table1, rule0, rule1)) => {
        println(table0)
        println(table1)
        println(rule0)
        println(rule1)
      }

      case Failure(th) => {
        println(th)
      }
    }

    log.info("waiting for queries")
    Await.ready(results, Duration.Inf)
  }

  def many {
    log.info("running trace query")
    val results = mongo.find_many(
      MongoActions.FindManyTracesByRequestId("bbe7798d-f3a7-4b96-af58-53d43b8b74d6"))
    results.onComplete {
      case Success(docs) => println(docs)
      case Failure(th) => println(th)
    }

    Await.ready(results, Duration.Inf)
  }
}

