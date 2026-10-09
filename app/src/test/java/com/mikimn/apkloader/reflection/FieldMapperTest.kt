package com.mikimn.apkloader.reflection

import androidx.core.util.Predicate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

open class BaseState {
    var inherited: String? = "base-default"
}

class ShadowSide : BaseState() {
    var name: String? = null
    var count: Int = 0
    var mismatch: Int = 7
    val frozen: String = "frozen-default"
    var onlyInShadow: String? = "keep"
}

class HostSide : BaseState() {
    var name: String? = null
    var count: Int = 0
    var mismatch: String? = "string-not-int"
    val frozen: String = "frozen-from-host"
    var onlyInHost: String? = "x"
}

class FieldMapperTest {
    private fun host() = HostSide().apply {
        inherited = "inherited-value"; name = "host"; count = 42
    }

    @Test fun copiesMatchingNameAndTypeFields() {
        val to = ShadowSide()
        FieldMapper.copy(to, host().let { ShadowSide().apply { name = it.name; count = it.count } })
        assertThat(to.name).isEqualTo("host")
        assertThat(to.count).isEqualTo(42)
    }

    // The actual use: copying between two *different* classes that share field names.
    @Test fun copiesAcrossDifferentClassesByName() {
        val to = ShadowSide()
        @Suppress("UNCHECKED_CAST")
        FieldMapper.copy(to as Any, host() as Any)
        assertThat(to.name).isEqualTo("host")
        assertThat(to.count).isEqualTo(42)
    }

    @Test fun copiesInheritedFields() {
        val to = ShadowSide()
        FieldMapper.copy(to as Any, host() as Any)
        assertThat(to.inherited).isEqualTo("inherited-value")
    }

    @Test fun skipsFieldsWhoseTypeDiffers() {
        val to = ShadowSide()
        FieldMapper.copy(to as Any, host() as Any)
        assertThat(to.mismatch).isEqualTo(7)
    }

    @Test fun neverTouchesFinalFields() {
        val to = ShadowSide()
        FieldMapper.copy(to as Any, host() as Any)
        assertThat(to.frozen).isEqualTo("frozen-default")
    }

    @Test fun leavesFieldsWithNoCounterpart() {
        val to = ShadowSide()
        FieldMapper.copy(to as Any, host() as Any)
        assertThat(to.onlyInShadow).isEqualTo("keep")
    }

    @Test fun nullSourceValueOverwritesTarget() {
        val to = ShadowSide().apply { name = "old" }
        FieldMapper.copy(to as Any, HostSide().apply { name = null } as Any)
        assertThat(to.name).isNull()
    }

    // DCLActivity uses this to exclude mWindowAdded from lifecycle syncs.
    @Test fun predicateCanExcludeFieldsByName() {
        val to = ShadowSide().apply { count = 1; name = "keep-me" }
        FieldMapper.copy(
            to as Any, host() as Any,
            Predicate { (field, _) -> field.name != "name" },
        )
        assertThat(to.name).isEqualTo("keep-me")
        assertThat(to.count).isEqualTo(42)
    }

    @Test fun predicateSeesTheValueBeingCopied() {
        val seen = mutableMapOf<String, Any?>()
        FieldMapper.copy(ShadowSide() as Any, host() as Any, Predicate { (f, v) -> seen[f.name] = v; true })
        assertThat(seen["count"]).isEqualTo(42)
        assertThat(seen["name"]).isEqualTo("host")
    }
}
