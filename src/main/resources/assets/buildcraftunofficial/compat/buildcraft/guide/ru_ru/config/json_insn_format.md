Файлы должны находиться в [...]
В самой первой строке должно быть
<json_insn>
~{buildcraft/json/insn}
</json_insn>
Всё остальное должно быть вызовом функции или комментарием. Существует 4 встроенные функции: add, remove, replace и modify. Описанные ниже операторы alias и import служат для определения и загрузки дополнительных функций.

<new_page/>
## Аргументы функций
Функции могут принимать 3 различных типа аргументов: числа, строки или многострочные строки.
Числа не могут содержать пробелы.
Строки должны начинаться и заканчиваться кавычкой (")
Многострочные строки должны начинаться и заканчиваться обратными кавычками (`)
Например, с функцией "remove":
<json_insn>
remove "buildcraftunofficial:oil"
remove `
buildcraftunofficial:oil
`
</json_insn>

<new_page/>
## Функция remove
Эта функция удаляет запись с указанным именем. Она принимает 1 аргумент — имя.
Например:
<json_insn>
remove "buildcraftunofficial:chipset_diamond"
</json_insn>

<new_page/>
## Функция add
Эта функция добавляет дополнительную запись с указанным именем. Она принимает либо 1 аргумент (имя), либо 2 аргумента (сначала имя, затем json).
Например:
<json_insn>
add "item/wrench" `{
    "title": "item.wrenchItem.name",
    "tag_type": "item",
    "tag_subtype": "tool"
}`
</json_insn>
Если json отсутствует, он будет загружен из файла, заданного именем.
Например, если вызов функции выглядит так:
<json_insn>
add "wrench"
</json_insn>
А файл insn находится в
<json_insn>
"assets/buildcraftunofficial/compat/buildcraft/guide.txt"
</json_insn>
то страница руководства для «wrench» будет загружена из
<json_insn>
"assets/buildcraftunofficial/compat/buildcraft/guide/item/wrench.md"
</json_insn>
<new_page/>
## Функция replace
Эта функция удаляет запись с первым именем и добавляет запись со вторым именем, но только если первая запись действительно была добавлена чем-то другим.
Эта функция принимает два аргумента: имя удаляемой записи и имя добавляемой.
Она также может принимать необязательный третий аргумент — json для добавления. (Он работает точно так же, как функция add, если не учитывать первый аргумент).
<new_page/>
## Функция modify
Эта функция работает очень похоже на функцию replace, за исключением того, что она наследует json-теги из удаляемой записи.

Например, если мы хотим добавить рецепт топлива для двигателя внутреннего сгорания из нефти, а затем позволить моду-аддону добавить генерацию остатка в качестве последующей модификации, мы можем сделать это так:
<json_insn>
add "oil" `{
 "fuel": {
  "id": "buildcraftunofficial:oil", "amount": 4
 },
 "power": 3
}`
</json_insn>
Тогда запись в модуле-аддоне могла бы выглядеть так:
<json_insn>
modify "buildcraftunofficial:oil" "oil" `{
 "residue": {
  "id": "buildcraftunofficial:oil_residue",
  "amount": 2
 }
}`
</json_insn>
В результате будет добавлен всего один рецепт топлива в пространстве имён модифицирующего мода, например "myaddon:oil"
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

(Примечание: рецепты топлива для двигателя внутреннего сгорания пока ещё нельзя настраивать таким образом — вам придётся дождаться обновления MJ, прежде чем это станет действительно возможным)
<new_page/>
## Пользовательские функции с помощью alias
(TODO: Пояснение)
<json_insn>
alias "add_fuel" 2 `
 add "%0" \`{
  "fuel": {
    "id": "${domain}:%0"
  }
 }\`
`
</json_insn>

## Использование готовых файлов alias
(TODO: Пояснение)
<json_insn>
import "buildcraftunofficial:util"
</json_insn>

Импортируемые файлы должны начинаться с
<json_insn>
~{buildcraft/json/lib}
</json_insn>
и могут дополнительно содержать количество аргументов
<json_insn>
~args 3
</json_insn>