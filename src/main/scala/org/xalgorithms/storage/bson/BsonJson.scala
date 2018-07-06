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
import org.mongodb.scala.Document
import play.api.libs.json._

object BsonJson {
  def toJson(v: BsonValue): JsValue = v match {
    case (bv: BsonBoolean)  => JsBoolean(bv.getValue())
    case (nv: BsonNumber)   => JsNumber(nv.doubleValue())
    case (sv: BsonString)   => JsString(sv.getValue())
    case (av: BsonArray)    => JsArray(av.getValues().asScala.map(toJson(_)))
    case (dv: BsonDocument) => JsObject(dv.entrySet().asScala.map { e =>
      (e.getKey(), toJson(e.getValue()))
    }.toMap)
    case _ => JsNull
  }

  object Implicits {
    implicit val val_writes = new Writes[BsonValue] {
      def writes(v: BsonValue) = toJson(v)
    }

    implicit val doc_writes = new Writes[Document] {
      def writes(doc: Document) = toJson(doc.toBsonDocument)
    }
  }
}
