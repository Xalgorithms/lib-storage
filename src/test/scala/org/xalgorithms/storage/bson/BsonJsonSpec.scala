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

import org.bson._
import play.api.libs.json._
import scala.collection.JavaConversions._

import org.scalamock.scalatest.MockFactory
import org.scalatest._

class BsonJsonSpec extends FlatSpec with Matchers with MockFactory {
  "BsonJson.toJson" should "convert booleans" in {
    BsonJson.toJson(BsonBoolean.TRUE) shouldEqual(JsBoolean(true))
    BsonJson.toJson(BsonBoolean.FALSE) shouldEqual(JsBoolean(false))
  }

  it should "convert numbers" in {
    val expects = Seq(
      (new BsonDouble(11.1), JsNumber(11.1)),
      (new BsonDouble(1000.345), JsNumber(1000.345)),
      (new BsonDouble(654321.0), JsNumber(654321.0)),
      (new BsonInt32(11111), JsNumber(11111.0)),
      (new BsonInt32(8888888), JsNumber(8888888.0)),
      (new BsonInt32(99999999), JsNumber(99999999.0)),
      (new BsonInt64(11111), JsNumber(11111.0)),
      (new BsonInt64(8888888), JsNumber(8888888.0)),
      (new BsonInt64(99999999), JsNumber(99999999.0))
    )

    expects.foreach { case (bv, jv) =>
      BsonJson.toJson(bv) shouldEqual(jv)
    }
  }

  it should "convert strings" in {
    val expects = Seq(
      (new BsonString("aaaaaaaaa"), JsString("aaaaaaaaa")),
      (new BsonString("anotheraa"), JsString("anotheraa")),
      (new BsonString("anrushaa"), JsString("anrushaa")),
      (new BsonString("anrumbleaa"), JsString("anrumbleaa"))
    )

    expects.foreach { case (bv, jv) =>
      BsonJson.toJson(bv) shouldEqual(jv)
    }
  }

  it should "convert arrays" in {
    val strings = Seq("aaaaaaaaa", "anotheraa", "anrushaa", "anrumbleaa")
    val numbers = Seq(11, 22, 33, 44)

    BsonJson.toJson(new BsonArray(strings.map(new BsonString(_)))) shouldEqual(JsArray(strings.map(JsString(_))))
    BsonJson.toJson(new BsonArray(numbers.map(new BsonInt32(_)))) shouldEqual(JsArray(numbers.map(JsNumber(_))))
  }

  it should "convert documents" in {
    val s = """{
      "a" : { "aa" : 1, "ab" : 2 },
      "b" : { "ba" : "BA", "bb" : "BB" },
      "c" : "CCCCC"
    }"""

    BsonJson.toJson(BsonDocument.parse(s)) shouldEqual(Json.parse(s))
  }
}
