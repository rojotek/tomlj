/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import static org.tomlj.TomlVersion.V0_4_0;

import org.tomlj.internal.TomlParser;
import org.tomlj.internal.TomlParserBaseVisitor;

import java.util.List;

final class LineVisitor extends TomlParserBaseVisitor<MutableTomlTable> {

  private final MutableTomlTable table = new MutableTomlTable();
  private final ErrorReporter errorReporter;
  private final TomlVersion version;
  private MutableTomlTable currentTable = table;

  LineVisitor(ErrorReporter errorReporter, TomlVersion version) {
    this.errorReporter = errorReporter;
    this.version = version;
  }

  @Override
  public MutableTomlTable visitKeyval(TomlParser.KeyvalContext ctx) {
    TomlParser.KeyContext keyContext = ctx.key();
    TomlParser.ValContext valContext = ctx.val();
    if (keyContext == null || valContext == null) {
      return table;
    }
    try {
      List<String> path = keyContext.accept(new KeyVisitor());
      if (path == null || path.isEmpty()) {
        return table;
      }
      // TOML 0.4.0 doesn't support dotted keys
      if (!version.after(V0_4_0) && path.size() > 1) {
        throw new TomlParseError("Dotted keys are not supported", new TomlPosition(keyContext));
      }
      Object value = valContext.accept(new ValueVisitor());
      if (value != null) {
        currentTable.set(path, value, new TomlPosition(ctx));
      }
      return table;
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
      return table;
    }
  }

  @Override
  public MutableTomlTable visitStandardTable(TomlParser.StandardTableContext ctx) {
    TomlParser.KeyContext keyContext = ctx.key();
    if (keyContext == null) {
      errorReporter.reportError(new TomlParseError("Empty table key", new TomlPosition(ctx)));
      return table;
    }
    List<String> path = keyContext.accept(new KeyVisitor());
    if (path == null) {
      return table;
    }
    try {
      currentTable = table.createTable(path, new TomlPosition(ctx));
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
    }
    return table;
  }

  @Override
  public MutableTomlTable visitArrayTable(TomlParser.ArrayTableContext ctx) {
    TomlParser.KeyContext keyContext = ctx.key();
    if (keyContext == null) {
      errorReporter.reportError(new TomlParseError("Empty table key", new TomlPosition(ctx)));
      return table;
    }
    List<String> path = keyContext.accept(new KeyVisitor());
    if (path == null) {
      return table;
    }
    try {
      currentTable = table.createArrayTable(path, new TomlPosition(ctx));
    } catch (TomlParseError e) {
      errorReporter.reportError(e);
    }
    return table;
  }

  @Override
  protected MutableTomlTable aggregateResult(MutableTomlTable aggregate, MutableTomlTable nextResult) {
    return aggregate == null ? null : nextResult;
  }

  @Override
  protected MutableTomlTable defaultResult() {
    return table;
  }
}
