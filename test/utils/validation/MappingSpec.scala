/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.utils.validation

import org.scalatest.mockito.MockitoSugar
import utils.validation.ConstraintUtil.{CompulsoryTextFieldMappingParameter, _}
import utils.validation.ErrorMessagesUtilAPI._
import utils.validation.MappingUtilAPI.{MappingUtil, _}
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatestplus.play.OneAppPerSuite
import play.api.data.Forms._
import play.api.data.validation.Invalid
import play.api.data.{Form, FormError}
import utils.TaxsValidator._


class MappingSpec extends WordSpecLike with Matchers with MockitoSugar with OneAppPerSuite {

  val defaultMaxLength = 10

  implicit class Helper(errors: Seq[FormError]) {
    def shouldContain(expected: Invalid) = {
      val transformed: Seq[(String, Seq[Any])] = errors.map { x =>
        (x.message, x.args)
      }
      transformed.contains((expected.errors.head.message, expected.errors.head.args))
    }
  }

  // this test evaluates the functionalities of the unbinding methods
  // this test expects valid form data as input
  // it fills the form then tests the fill method of the form.
  // the correctness of the fill method depends on the unbinding method
  def performFillFormTest[T](form: Form[T], data: FormData) {
    form
      .bind(data)
      .fold(errors => {}, model => {
        val form2 = form.fill(model)
        form2.fold(form2WithErrors => {}, model_2 => {
          model shouldBe model_2
        })
      })
  }

  val defaultCompulsoryMapping = (fieldId: String) =>
    compulsoryText(
      CompulsoryTextFieldMappingParameter(
        simpleFieldIsEmptyConstraintParameter(fieldId, "testkey1"),
        genericFieldMaxLengthConstraintParameter(defaultMaxLength, fieldId, fieldNameInErrorMessage = ""),
        genericInvalidFormatConstraintParameter(validText, fieldId, fieldNameInErrorMessage = "")
      ))

  val defaultOptionalMapping = (fieldId: String) =>
    optionalText(
      OptionalTextFieldMappingParameter(
        genericFieldMaxLengthConstraintParameter(defaultMaxLength, fieldId, fieldNameInErrorMessage = ""),
        genericInvalidFormatConstraintParameter(validText, fieldId, fieldNameInErrorMessage = "")
      ))

  "For single layer models" should {
    "mapping validations" should {
      case class Test(
        field1: Option[String],
        field1a: String,
        field2: Option[String],
        ignoredField: Option[String],
        ignoredField2: Option[String])

      val alwaysIgnore: Option[FormQuery] = (data: FormData) => false

      implicit val form = Form(
        mapping(
          "field1"        -> defaultCompulsoryMapping("field1"),
          "field1a"       -> defaultCompulsoryMapping("field1a").toStringFormatter,
          "field2"        -> defaultOptionalMapping("field2"),
          "ignoredField"  -> (defaultCompulsoryMapping("ignoredField") iff alwaysIgnore),
          "ignoredField2" -> (defaultOptionalMapping("ignoredField2") iff alwaysIgnore)
        )(Test.apply)(Test.unapply))

      "accept valid data, and can be populated by valid case class" in {
        val data = FormData("field1" -> "me", "field1a" -> "me")
        performFillFormTest(form, data)
      }

      "ignored fields should not be entered into the resulting case class" in {
        val data = FormData("field1" -> "me", "field1a" -> "me2", "ignoredField" -> "data", "ignoredField" -> "data2")
        form
          .bind(data)
          .fold(
            errors => {},
            model => {
              model.ignoredField shouldBe None
              model.ignoredField2 shouldBe None
              model.field1 shouldBe Some("me")
              model.field1a shouldBe "me2"
            }
          )
      }
    }

    "function correctly when there is crossField validation" should {

      def field1IsEmpty(): Option[FormQuery] =
        (data: FormData) => data.getOrElse("field1", "").equals("")

      // if a field is conditionally dependent on another then it cannot be of a String type
      case class Test(field1: Option[String], field2: Option[String], field3: Option[String])
      implicit val form = Form(
        mapping(
          "field1" -> optional(text),
          "field2" -> (defaultCompulsoryMapping("field2") iff field1IsEmpty),
          "field3" -> (defaultOptionalMapping("field3") iff field1IsEmpty)
        )(Test.apply)(Test.unapply))

      "accept valid data, and can be populated by valid case class" in {
        val data = FormData("field1" -> "", "field2" -> "me", "field3" -> "me")
        performFillFormTest(form, data)
      }
    }
  }

  "For multi-layer models" should {
    "function correctly when it is used inside a submap" should {

      def field1IsEmpty(): Option[FormQuery] =
        (data: FormData) => data.getOrElse("sub.field1", "").equals("")

      case class SubTest(field1: Option[String], field2: Option[String])
      case class Test(field1: SubTest)

      def attachPrefix(prefix: Option[String], fieldId: String): String =
        prefix match {
          case None    => fieldId
          case Some(p) => f"$p.$fieldId"
        }

      // for sub mappings the invalid messages could change depending on the prefix
      // therefore all submappings should be configurable functions to allow customisation
      val submap = (prefix: Option[String]) =>
        mapping(
          "field1" -> optional(text),
          "field2" -> (defaultCompulsoryMapping(attachPrefix(prefix, "field2")) iff field1IsEmpty)
        )(SubTest.apply)(SubTest.unapply)

      implicit val form = Form(
        mapping(
          "sub" -> submap(Some("sub"))
        )(Test.apply)(Test.unapply))

      "accept valid data, and can be populated by valid case class" in {
        val data = FormData("sub.field1" -> "", "sub.field2" -> "me")
        performFillFormTest(form, data)
      }
    }
  }
}
