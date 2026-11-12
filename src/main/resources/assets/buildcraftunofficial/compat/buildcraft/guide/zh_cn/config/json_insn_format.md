文件应位于 [...] 中
第一行必须是
<json_insn>
~{buildcraft/json/insn}
</json_insn>
其余所有内容必须是函数调用或注释。共有 4 种内建函数：add、remove、replace 和 modify。下文所述的 alias 和 import 语句用于定义和加载额外的函数。

<new_page/>
## 函数参数
函数可以接受 3 种不同类型的参数：数字、字符串或多行字符串。
数字不能包含空格。
字符串必须以引号（"）开头和结尾
多行字符串必须以反引号（`）开头和结尾
以 "remove" 函数为例：
<json_insn>
remove "buildcraftunofficial:oil"
remove `
buildcraftunofficial:oil
`
</json_insn>

<new_page/>
## remove 函数
此函数会移除指定名称的条目。它接受 1 个参数，即名称。
例如：
<json_insn>
remove "buildcraftunofficial:chipset_diamond"
</json_insn>

<new_page/>
## add 函数
此函数会添加一个具有指定名称的新条目。它接受 1 个参数（名称）或 2 个参数（名称，然后是 json）。
例如：
<json_insn>
add "item/wrench" `{
    "title": "item.wrenchItem.name",
    "tag_type": "item",
    "tag_subtype": "tool"
}`
</json_insn>
如果缺少 json，则会从名称指定的文件中加载。
例如，如果函数调用是：
<json_insn>
add "wrench"
</json_insn>
而 insn 文件位于
<json_insn>
"assets/buildcraftunofficial/compat/buildcraft/guide.txt"
</json_insn>
那么扳手的指南页面将会从以下文件加载：
<json_insn>
"assets/buildcraftunofficial/compat/buildcraft/guide/item/wrench.md"
</json_insn>
<new_page/>
## replace 函数
此函数会移除与第一个名称对应的条目，并添加一个使用第二个名称的条目，但仅当第一个条目确实是由其他内容添加时才会生效。
此函数接受两个参数：要移除的名称和要添加的名称。
它还可以接受可选的第三个参数，即要添加的 json。（如果忽略第一个参数，它的作用与 add 函数完全相同。）
<new_page/>
## modify 函数
此函数的作用方式与 replace 函数非常相似，不同之处在于它会继承被移除条目的 json 标签。

例如，如果我们想为原油添加一条燃油引擎燃料配方，然后让一个附属模组以后续修改的方式加入残渣生成，就可以这样写：
<json_insn>
add "oil" `{
 "fuel": {
  "id": "buildcraftunofficial:oil", "amount": 4
 },
 "power": 3
}`
</json_insn>
那么附属模组中的条目可能会是这样：
<json_insn>
modify "buildcraftunofficial:oil" "oil" `{
 "residue": {
  "id": "buildcraftunofficial:oil_residue",
  "amount": 2
 }
}`
</json_insn>
这样最终只会添加一条燃料配方，且位于发起修改的模组的命名空间下，例如 "myaddon:oil"
<json_insn>
{
 "fuel": {
  "id": "buildcraftunofficial:oil", "amount": 4
 },
 "residue": {
  "id": "buildcraftunofficial:oil_residue",
  "amount": 2
 },
 "power": 3
}
</json_insn>

（注意：燃油引擎燃料配方目前还不能通过这种方式配置——你需要等到 MJ 更新之后，这样写才会真正生效）
<new_page/>
## 使用 alias 自定义函数
（TODO：说明）
<json_insn>
alias "add_fuel" 2 `
 add "%0" \`{
  "fuel": {
    "id": "${domain}:%0"
  }
 }\`
`
</json_insn>

## 使用预制的 alias 文件
（TODO：说明）
<json_insn>
import "buildcraftunofficial:util"
</json_insn>

被导入的文件必须以下面的内容开头：
<json_insn>
~{buildcraft/json/lib}
</json_insn>
并且可以选择附带一个参数个数：
<json_insn>
~args 3
</json_insn>
