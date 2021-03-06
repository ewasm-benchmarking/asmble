package asmble.ast

import asmble.io.SExprToStr

/**
 * Ast representation of wasm S-expressions (wast format).
 * see [[https://webassembly.github.io/spec/core/text/index.html]]
 */
sealed class SExpr {
    
    data class Multi(val vals: List<SExpr> = emptyList()) : SExpr() {
        override fun toString() = SExprToStr.Compact.fromSExpr(this)
    }

    data class Symbol(
        val contents: String = "",
        val quoted: Boolean = false,
        val hasNonUtf8ByteSeqs: Boolean = false
    ) : SExpr() {
        override fun toString() = SExprToStr.Compact.fromSExpr(this)
        // This is basically the same as the deprecated java.lang.String#getBytes
        fun rawContentCharsToBytes() = contents.toCharArray().map(Char::toByte)
    }

}