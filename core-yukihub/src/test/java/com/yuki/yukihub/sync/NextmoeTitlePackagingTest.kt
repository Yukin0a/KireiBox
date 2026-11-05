package com.yuki.yukihub.sync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NextmoeTitlePackagingTest {
    @Test fun engineSuffixDoesNotHideExactTitle() {
        assertEquals(NextmoeTitleIdentity.key("ライムライト"), NextmoeTitleIdentity.key("ライムライトkrkr"))
        assertEquals(NextmoeTitleIdentity.key("寄宿之恋"), NextmoeTitleIdentity.key("【KRKR】寄宿之恋"))
    }

    @Test fun combinedPackagingLabelIsIgnored() {
        assertEquals(NextmoeTitleIdentity.key("お嬢様"), NextmoeTitleIdentity.key("【PC+KRKR】お嬢様"))
    }

    @Test fun sequelAndSubtitleRemainDistinct() {
        assertNotEquals(NextmoeTitleIdentity.key("作品"), NextmoeTitleIdentity.key("作品2"))
        assertNotEquals(NextmoeTitleIdentity.key("金色Loveriche"), NextmoeTitleIdentity.key("金色Loveriche Golden Time"))
    }
}
