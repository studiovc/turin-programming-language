package me.tomassetti.turin.symbols;

import me.tomassetti.turin.parser.analysis.resolvers.SymbolResolver;
import me.tomassetti.turin.parser.ast.typeusage.TypeUsage;

/**
 * Something generic: it could come from an AST or be a loaded value.
 */
public interface Symbol {
    TypeUsage calcType(SymbolResolver resolver);
}
