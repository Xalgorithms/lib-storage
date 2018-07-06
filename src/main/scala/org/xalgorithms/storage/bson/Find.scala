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
import collection.immutable
import org.bson._
import org.joda.time.DateTime

object Find {
  def maybe_find_text(doc: BsonDocument, k: String): Option[String] = {
    maybe_find_value(doc, k) match {
      case Some(v) => Option(convert_to_string(v))
      case None    => None
    }
  }

  def maybe_find_many_texts(doc: BsonDocument, ks: Seq[String]): Seq[String] = {
    maybe_find_many_values(doc, ks).foldLeft(Seq[String]()) { (seq, v) =>
      convert_to_string(v) match {
        case null => seq
        case (s: String) => seq :+ s
      }
    }
  }

  def maybe_find_first_text(doc: BsonDocument, ks: Seq[String]): Option[String] = {
    val vals = maybe_find_many_texts(doc, ks)
    vals.size match {
      case 0 => None
      case _ => Some(vals.head)
    }
  }

  def maybe_find_document(doc: BsonDocument, k: String): Option[BsonDocument] = {
    maybe_find_value(doc, k) match {
      case Some(v) => Option(convert_to_document(v))
      case None    => None
    }
  }

  def maybe_find_many_documents(doc: BsonDocument, ks: Seq[String]): Seq[BsonDocument] = {
    maybe_find_many_values(doc, ks).foldLeft(Seq[BsonDocument]()) { (seq, v) =>
      convert_to_document(v) match {
        case null => seq
        case doc => seq :+ doc
      }
    }
  }

  def maybe_find_first_document(doc: BsonDocument, ks: Seq[String]): Option[BsonDocument] = {
    val vals = maybe_find_many_documents(doc, ks)
    vals.size match {
      case 0 => None
      case _ => Some(vals.head)
    }
  }

  def maybe_find_array(doc: BsonDocument, k: String): Option[BsonArray] = {
    maybe_find_value(doc, k) match {
      case Some(v) => v match {
        case (a: BsonArray) => Some(a)
        case _ => None
      }
      case _ => None
    }
  }

  def maybe_find_array_as_seq(doc: BsonDocument, k: String): Option[Seq[BsonValue]] = {
    maybe_find_value(doc, k) match {
      case Some(v) => Option(convert_to_seq(v))
      case _ => None
    }
  }

  def maybe_find_date_time(doc: BsonDocument, k: String): Option[DateTime] = {
     maybe_find_value(doc, k) match {
      case Some(v) => Option(convert_to_date_time(v))
      case None    => None
    }
  }

  def maybe_find_many_values(doc: BsonDocument, ks: Seq[String]): Seq[BsonValue] = {
    ks.foldLeft(Seq[BsonValue]()) { (seq, k) =>
      maybe_find_value(doc, k) match {
        case Some(v) => seq :+ v
        case None    => seq
      }
    }
  }

  def maybe_find_first_value(doc: BsonDocument, ks: Seq[String]): Option[BsonValue] = {
    val ms = maybe_find_many_values(doc, ks)
    ms.size match {
      case 0 => None
      case _ => Some(ms.head)
    }
  }

  def maybe_find_value(doc: BsonDocument, k: String): Option[BsonValue] = {
    maybe_find_value(doc, k.split('.'))
  }

  def maybe_find_value(doc: BsonDocument, ks: Seq[String]): Option[BsonValue] = ks.size match {
    case 1 => Option(doc.get(ks.head, null))
    case len if len > 1 => {
      maybe_find_value(Option(doc.getDocument(ks.head, null)), ks.tail)
    }
    case _ => None
  }

  def maybe_find_value(opt_doc: Option[BsonDocument], ks: Seq[String]): Option[BsonValue] = opt_doc match {
    case Some(doc) => maybe_find_value(doc, ks)
    case None      => None
  }

  def convert_to_document(v: BsonValue): BsonDocument = v match {
    case (dv: BsonDocument) => dv
    case _ => null
  }

  def convert_to_date_time(v: BsonValue): DateTime = v match {
    case (nv: BsonString)    => {
      try {
        new DateTime(nv.getValue())
      } catch {
        case _: Throwable => null
      }
    }

    case (nv: BsonTimestamp) => new DateTime(nv.getTime().toLong * 1000)
    case (nv: BsonDateTime)  => new DateTime(nv.getValue())
    case _                   => null
  }

  def convert_to_string(v: BsonValue): String = v match {
    // case (nv: BsonBoolean)  => nv.toString()
    // case BsonType.DATE_TIME  => v.asDateTime().()
    // case BsonType.DOUBLE  => v.asDouble().toString()
    // case BsonType.INT64  => v.asInt64().toString()
    // case BsonType.INT32  => v.asInt32().toString()
    case (nv: BsonString)  => nv.getValue()
    // case BsonType.TIMESTAMP  => match_value(v.asTimestamp())
    case _ => null
  }

  def convert_to_seq(v: BsonValue): Seq[BsonValue] = v match {
    case (av: BsonArray) => av.getValues().asScala
    case _ => null
  }
}
