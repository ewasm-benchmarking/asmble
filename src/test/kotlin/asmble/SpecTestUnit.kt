package asmble

import asmble.ast.SExpr
import asmble.ast.Script
import asmble.io.SExprToAst
import asmble.io.StrToSExpr
import asmble.run.jvm.ScriptAssertionError
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

class SpecTestUnit(val name: String, val wast: String, val expectedOutput: String?) {

    override fun toString() = "Spec unit: $name"

    val shouldFail get() = name.endsWith(".fail")

    val defaultMaxMemPages get() = when (name) {
        "nop"-> 20
        "resizing" -> 830
        else -> 1
    }

    val parseResult: StrToSExpr.ParseResult.Success by lazy {
        StrToSExpr.parse(wast).let {
            when (it) {
                is StrToSExpr.ParseResult.Error -> throw Exception("$name[${it.pos}] Parse fail: ${it.msg}")
                is StrToSExpr.ParseResult.Success -> it
            }
        }
    }

    val ast: List<SExpr> get() = parseResult.vals

    val script: Script by lazy { SExprToAst.toScript(SExpr.Multi(ast)) }

    fun isWarningInsteadOfError(t: Throwable) = testsWithErrorToWarningPredicates[name]?.invoke(t) ?: false

    companion object {

        /*
        TODO: We are going down in order. One's we have not yet handled:
        - br_table.wast - Table issues on jumps
        - imports.wast - No memory exports yet
        - left-to-right.wast - Not handling tables yet
        - linking.wast - Not handling tables yet
        - return.wast - Not handling tables yet
        - switch.wast - Table issues on jumps (ref "argument switch")
        - typecheck.wast - Not handling tables yet
        - unreachable.wast - Not handling tables yet
        */

        val knownGoodTests = arrayOf(
            "address.wast",
            "address-offset-range.fail.wast",
            "binary.wast",
            "block.wast",
            "block-end-label-mismatch.fail.wast",
            "block-end-label-superfluous.wast",
            "br.wast",
            "br_if.wast",
            "break-drop.wast",
            "call.wast",
            "call_indirect.wast",
            "comments.wast",
            "conversions.wast",
            "custom_section.wast",
            "endianness.wast",
            "exports.wast",
            "f32.load32.fail.wast",
            "f32.load64.fail.wast",
            "f32.store32.fail.wast",
            "f32.store64.fail.wast",
            "f32.wast",
            "f32_cmp.wast",
            "f64.load32.fail.wast",
            "f64.load64.fail.wast",
            "f64.store32.fail.wast",
            "f64.store64.fail.wast",
            "f64.wast",
            "f64_cmp.wast",
            "fac.wast",
            "float_exprs.wast",
            "float_literals.wast",
            "float_memory.wast",
            "float_misc.wast",
            "forward.wast",
            "func.wast",
            "func_ptrs.wast",
            "func-local-after-body.fail.wast",
            "func-local-before-param.fail.wast",
            "func-local-before-result.fail.wast",
            "func-param-after-body.fail.wast",
            "func-result-after-body.fail.wast",
            "func-result-before-param.fail.wast",
            "get_local.wast",
            "globals.wast",
            "i32.load32_s.fail.wast",
            "i32.load32_u.fail.wast",
            "i32.load64_s.fail.wast",
            "i32.load64_u.fail.wast",
            "i32.store32.fail.wast",
            "i32.store64.fail.wast",
            "i32.wast",
            "i64.load64_s.fail.wast",
            "i64.load64_u.fail.wast",
            "i64.store64.fail.wast",
            "i64.wast",
            "if.wast",
            "if-else-end-label-mismatch.fail.wast",
            "if-else-end-label-superfluous.fail.wast",
            "if-else-label-mismatch.fail.wast",
            "if-else-label-superfluous.fail.wast",
            "if-end-label-mismatch.fail.wast",
            "if-end-label-superfluous.fail.wast",
            "import-after-func.fail.wast",
            "import-after-global.fail.wast",
            "import-after-memory.fail.wast",
            "import-after-table.fail.wast",
            "int_exprs.wast",
            "int_literals.wast",
            "labels.wast",
            "load-align-0.fail.wast",
            "load-align-odd.fail.wast",
            "loop.wast",
            "loop-end-label-mismatch.fail.wast",
            "loop-end-label-superfluous.fail.wast",
            "memory.wast",
            "memory_redundancy.wast",
            "memory_trap.wast",
            "names.wast",
            "nop.wast",
            "of_string-overflow-hex-u32.fail.wast",
            "of_string-overflow-hex-u64.fail.wast",
            "of_string-overflow-s32.fail.wast",
            "of_string-overflow-s64.fail.wast",
            "of_string-overflow-u32.fail.wast",
            "of_string-overflow-u64.fail.wast",
            "resizing.wast",
            "select.wast",
            "set_local.wast",
            "skip-stack-guard-page.wast",
            "stack.wast",
            "start.wast",
            "store-align-0.fail.wast",
            "store-align-odd.fail.wast",
            "store_retval.wast",
            "tee_local.wast",
            "traps.wast",
            "unreached-invalid.wast",
            "unwind.wast"
        )

        val testsWithErrorToWarningPredicates: Map<String, (Throwable) -> Boolean> = mapOf(
            // NaN bit patterns can be off
            "float_literals" to this::isNanMismatch,
            "float_exprs" to this::isNanMismatch
        )

        fun isNanMismatch(t: Throwable) = t is ScriptAssertionError && (
            t.assertion is Script.Cmd.Assertion.ReturnNan ||
            (t.assertion is Script.Cmd.Assertion.Return && (t.assertion as Script.Cmd.Assertion.Return).let {
                (it.action as? Script.Cmd.Action.Invoke)?.string?.contains("nan") ?: false
            })
        )

        val unitsPath = "/spec/test/core"

        val allUnits by lazy { loadFromResourcePath("/local-spec") + loadFromResourcePath(unitsPath) }

        fun loadFromResourcePath(basePath: String): List<SpecTestUnit> {
            require(basePath.last() != '/')
            val jcls = SpecTestUnit::class.java
            val uri = jcls.getResource(basePath).toURI()
            val fs = if (uri.scheme == "jar") FileSystems.newFileSystem(uri, emptyMap<String, Any>()) else null
            fs.use { fs ->
                val path = fs?.getPath(basePath) ?: Paths.get(uri)
                val testWastFiles = Files.walk(path, 1).
                    filter { it.toString().endsWith(".wast") }.
                    // TODO: remove this when they all succeed
                    filter { knownGoodTests.contains(it.fileName.toString()) }
                return testWastFiles.map {
                    val name = it.fileName.toString().substringBeforeLast(".wast")
                    SpecTestUnit(
                        name = name,
                        wast = it.toUri().toURL().readText(),
                        expectedOutput = jcls.getResource("$basePath/expected-output/$name.wast.log")?.readText()
                    )
                }.collect(Collectors.toList())
            }
        }
    }
}