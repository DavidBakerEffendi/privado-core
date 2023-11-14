/*
 * This file is part of Privado OSS.
 *
 * Privado is an open source static code analysis tool to discover data flows in the code.
 * Copyright (C) 2022 Privado, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, contact support@privado.ai
 *
 */
package ai.privado.languageEngine.go.passes.orm

import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.codepropertygraph.generated.{Cpg, EdgeTypes}
import io.shiftleft.semanticcpg.language.*
import org.slf4j.LoggerFactory

class GorpParser(cpg: Cpg) extends BaseORMParser(cpg) {
  val GORP_PARAMETER_TYPE_RULE = "github.com/go-gorp/gorp.*"
  // when we pass a object's variable as its address its written as like `&users`
  // In joern its signatured as *[]packagename.User
  val ADDRESS_OF_OBJECT_SYMBOL = "*[]"

  override def generateParts(): Array[_ <: AnyRef] = {

    val typeFullNames = cpg.call
      .methodFullName(GORP_PARAMETER_TYPE_RULE)
      .argument
      .isCall
      .typeFullName
      .map(x => x.stripPrefix(ADDRESS_OF_OBJECT_SYMBOL))
      .dedup
      .l
    cpg.typeDecl.fullName(typeFullNames.mkString("|")).dedup.toArray
  }

}