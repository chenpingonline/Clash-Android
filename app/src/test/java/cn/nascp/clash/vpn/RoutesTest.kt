package cn.nascp.clash.vpn
import org.junit.Assert.*
import org.junit.Test
class RoutesTest {
 @Test fun subtractsLanWithoutDroppingPublicAddresses(){
  val routes=Routes.included(listOf("10.0.0.0/8","192.168.1.0/24"))
  fun contains(ip:String):Boolean{val address=Routes.parse("$ip/32").address;return routes.any{address>=it.address&&address<it.address+it.size}}
  assertFalse(contains("10.1.2.3"));assertFalse(contains("192.168.1.254"));assertTrue(contains("1.1.1.1"));assertTrue(contains("192.168.2.1"));assertTrue(contains("255.255.255.255"))
 }
 @Test fun duplicateAndNestedExclusionsAreStable(){assertEquals(Routes.included(listOf("10.0.0.0/8")),Routes.included(listOf("10.0.0.0/8","10.0.0.0/8","10.1.0.0/16")))}
 @Test(expected=IllegalArgumentException::class)fun rejectsEmptyRouting(){Routes.included(listOf("0.0.0.0/0"))}
}
