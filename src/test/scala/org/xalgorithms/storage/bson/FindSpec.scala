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

import collection.JavaConverters._
import org.bson._
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest._

class FindSpec extends FlatSpec with Matchers with MockFactory {
  val doc = BsonDocument.parse("""{
    "a" : 1,
    "b" : "BB",
    "c" : {
      "ca" : "1",
      "cb" : 2
    },
    "d" : "DD",
    "e" : {
      "ea" : "EA",
      "eb" : "EB",
      "ec" : "EC"
    },
   "f" : [0, 1, 2]
  }""")

  "Find" should "find single text values" in {
    Find.maybe_find_text(doc, "a") shouldEqual(None)
    Find.maybe_find_text(doc, "b") shouldEqual(Some("BB"))
    Find.maybe_find_text(doc, "c") shouldEqual(None)
    Find.maybe_find_text(doc, "c.ca") shouldEqual(Some("1"))
    Find.maybe_find_text(doc, "c.cb") shouldEqual(None)
  }

  it should "find many text values" in {
    Find.maybe_find_many_texts(doc, Seq("a", "b")) shouldEqual(Seq("BB"))
    Find.maybe_find_many_texts(doc, Seq("b", "c.ca")) shouldEqual(Seq("BB", "1"))
    Find.maybe_find_many_texts(doc, Seq("c.ca", "c.cb")) shouldEqual(Seq("1"))
  }

  it should "find the first text value" in {
    Find.maybe_find_first_text(doc, Seq("a", "b")) shouldEqual(Some("BB"))
    Find.maybe_find_first_text(doc, Seq("b", "d")) shouldEqual(Some("BB"))
    Find.maybe_find_first_text(doc, Seq("d", "c.cb", "c.ca", "b")) shouldEqual(Some("DD"))
  }

  it should "find single document values" in {
    Find.maybe_find_document(doc, "a") shouldEqual(None)
    Find.maybe_find_document(doc, "c") shouldEqual(Some(doc.getDocument("c")))
    Find.maybe_find_document(doc, "e") shouldEqual(Some(doc.getDocument("e")))
  }

  it should "find many document values" in {
    Find.maybe_find_many_documents(doc, Seq("a", "c")) shouldEqual(Seq(doc.getDocument("c")))
    Find.maybe_find_many_documents(doc, Seq("e", "c")) shouldEqual(Seq(doc.getDocument("e"), doc.getDocument("c")))
  }

  it should "find the first document value" in {
    Find.maybe_find_first_document(doc, Seq("a", "c")) shouldEqual(Some(doc.getDocument("c")))
    Find.maybe_find_first_document(doc, Seq("e", "c")) shouldEqual(Some(doc.getDocument("e")))
  }

  it should "find arrays" in {
    Find.maybe_find_array(doc, "a") shouldEqual(None)
    Find.maybe_find_array(doc, "f") shouldEqual(Some(doc.getArray("f")))
  }

  it should "find arrays as sequences" in {
    Find.maybe_find_array_as_seq(doc, "a") shouldEqual(None)
    Find.maybe_find_array_as_seq(doc, "f") shouldEqual(Some(doc.getArray("f").getValues().asScala))
  }

  it should "find datetimes" in {
    val dt = new DateTime()
    doc.put("g", new BsonDateTime(dt.getMillis()))
    Find.maybe_find_date_time(doc, "a") shouldEqual(None)
    Find.maybe_find_date_time(doc, "g") shouldEqual(Some(dt))
  }

  it should "find single values" in {
    Find.maybe_find_value(doc, "a") shouldEqual(Some(doc.getInt32("a")))
    Find.maybe_find_value(doc, "b") shouldEqual(Some(doc.getString("b")))
    Find.maybe_find_value(doc, "c") shouldEqual(Some(doc.getDocument("c")))
    Find.maybe_find_value(doc, "h") shouldEqual(None)
    Find.maybe_find_value(doc, "i") shouldEqual(None)
  }

  it should "find many values" in {
    Find.maybe_find_many_values(doc, Seq("a", "h", "b")) shouldEqual(Seq(
      doc.getInt32("a"),
      doc.getString("b")
    ))
    Find.maybe_find_many_values(doc, Seq("a", "c", "b")) shouldEqual(Seq(
      doc.getInt32("a"),
      doc.getDocument("c"),
      doc.getString("b")
    ))
  }

  it should "find the first value" in {
    Find.maybe_find_first_value(doc, Seq("h", "a", "b")) shouldEqual(Some(doc.getInt32("a")))
    Find.maybe_find_first_value(doc, Seq("c.cc", "c.ca")) shouldEqual(Some(doc.getDocument("c").getString("ca")))
  }
}
