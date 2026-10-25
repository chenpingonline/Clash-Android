package cn.nascp.clash.vpn

/** Pure IPv4 CIDR subtraction, used on API 29+ where excludeRoute is unavailable. */
object Routes {
    data class Block(val address:Long,val prefix:Int) {
        fun text()=(24 downTo 0 step 8).joinToString("."){((address shr it) and 255).toString()}
        val size:Long get()=1L shl (32-prefix)
    }
    fun parse(value:String):Block {
        val parts=value.trim().split('/');require(parts.size==2){"排除网段需使用 CIDR"}
        val octets=parts[0].split('.');require(octets.size==4)
        val prefix=parts[1].toInt();require(prefix in 0..32)
        var address=0L;octets.forEach { val n=it.toInt();require(n in 0..255);address=(address shl 8) or n.toLong() }
        val mask=if(prefix==0)0L else (0xffffffffL shl (32-prefix)) and 0xffffffffL
        return Block(address and mask,prefix)
    }
    fun included(exclusions:List<String>):List<Block> {
        var routes=listOf(Block(0,0))
        exclusions.filter{it.isNotBlank()}.map(::parse).forEach { excluded->
            fun subtract(block:Block):List<Block>{
                if(block.address+block.size<=excluded.address || excluded.address+excluded.size<=block.address)return listOf(block)
                if(excluded.address<=block.address && excluded.address+excluded.size>=block.address+block.size)return emptyList()
                val next=block.prefix+1;return subtract(Block(block.address,next))+subtract(Block(block.address+(block.size/2),next))
            }
            routes=routes.flatMap(::subtract)
        }
        require(routes.isNotEmpty()){ "不能排除全部 IPv4 路由" };return routes
    }
}
