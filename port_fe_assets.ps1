$ErrorActionPreference = 'Stop'

$resources = "E:\GitHub\BuildCraft\buildcraft_resources\assets"
$energyAss = "E:\GitHub\BuildCraft\buildcraft-energy\src\main\resources\assets\buildcraftunofficial"
$transportAss = "E:\GitHub\BuildCraft\buildcraft-transport\src\main\resources\assets\buildcraftunofficial"

# 1. Block Textures
$engineTarget = "$energyAss\textures\block\engine\fe"
New-Item -ItemType Directory -Force -Path $engineTarget | Out-Null
Copy-Item "$resources\buildcraftenergy\textures\blocks\engine\rf\back.png" -Destination "$engineTarget\back.png"
Copy-Item "$resources\buildcraftenergy\textures\blocks\engine\rf\side.png" -Destination "$engineTarget\side.png"

$dynamoTarget = "$energyAss\textures\block\mj_dynamo"
New-Item -ItemType Directory -Force -Path $dynamoTarget | Out-Null
Copy-Item "$resources\buildcraftenergy\textures\blocks\mj_dynamo\back.png" -Destination "$dynamoTarget\back.png"
Copy-Item "$resources\buildcraftenergy\textures\blocks\mj_dynamo\side.png" -Destination "$dynamoTarget\side.png"
Copy-Item "$resources\buildcraftenergy\textures\blocks\mj_dynamo\front.png" -Destination "$dynamoTarget\front.png"

# 2. Compat Models
@"
{
    "textures": {
        "#back":"buildcraftunofficial:block/engine/fe/back",
        "#side":"buildcraftunofficial:block/engine/fe/side"
    },
    "parent":"buildcraftunofficial:compat_models/engine_base"
}
"@ | Out-File "$energyAss\compat_models\engine_fe.json" -Encoding UTF8

@"
{
    "textures": {
        "#back":"buildcraftunofficial:block/mj_dynamo/back",
        "#side":"buildcraftunofficial:block/mj_dynamo/side",
        "#front":"buildcraftunofficial:block/mj_dynamo/front"
    },
    "parent":"buildcraftunofficial:compat_models/engine_base"
}
"@ | Out-File "$energyAss\compat_models\engine_dynamo.json" -Encoding UTF8

# 3. Blockstates
$bsContent = @"
{
    "variants": {
        "facing=up": { "model": "buildcraftunofficial:block/___MODEL___" },
        "facing=down": { "model": "buildcraftunofficial:block/___MODEL___", "x": 180 },
        "facing=north": { "model": "buildcraftunofficial:block/___MODEL___", "x": 90, "y": 180 },
        "facing=south": { "model": "buildcraftunofficial:block/___MODEL___", "x": 90 },
        "facing=west": { "model": "buildcraftunofficial:block/___MODEL___", "x": 90, "y": 90 },
        "facing=east": { "model": "buildcraftunofficial:block/___MODEL___", "x": 90, "y": 270 }
    }
}
"@
$bsContent.Replace("___MODEL___", "engine_fe") | Out-File "$energyAss\blockstates\engine_fe.json" -Encoding UTF8
$bsContent.Replace("___MODEL___", "mj_dynamo") | Out-File "$energyAss\blockstates\mj_dynamo.json" -Encoding UTF8

# 4. Block Models
@"
{
    "parent": "buildcraftunofficial:block/engine_base",
    "textures": {
        "particle": "buildcraftunofficial:block/engine/fe/back",
        "back": "buildcraftunofficial:block/engine/fe/back",
        "side": "buildcraftunofficial:block/engine/fe/side"
    }
}
"@ | Out-File "$energyAss\models\block\engine_fe.json" -Encoding UTF8

@"
{
    "parent": "buildcraftunofficial:block/engine_base",
    "textures": {
        "particle": "buildcraftunofficial:block/mj_dynamo/back",
        "back": "buildcraftunofficial:block/mj_dynamo/back",
        "side": "buildcraftunofficial:block/mj_dynamo/side",
        "front": "buildcraftunofficial:block/mj_dynamo/front"
    }
}
"@ | Out-File "$energyAss\models\block\mj_dynamo.json" -Encoding UTF8

# 5. Item Models
@"
{
    "parent": "buildcraftunofficial:block/engine_fe"
}
"@ | Out-File "$energyAss\models\item\engine_fe.json" -Encoding UTF8

@"
{
    "parent": "buildcraftunofficial:block/mj_dynamo"
}
"@ | Out-File "$energyAss\models\item\mj_dynamo.json" -Encoding UTF8

# 6. FE Pipes Translation & Textures
$transportPipes = "$resources\buildcrafttransport\textures\pipes"
$transportPipesTarget = "$transportAss\textures\pipes"

# Copy and replace _rf -> _fe in filename inside transport textures
Get-ChildItem -Path $transportPipes -Filter "*rf*" | ForEach-Object {
    $newName = $_.Name -replace "_rf", "_fe"
    Copy-Item $_.FullName -Destination "$transportPipesTarget\$newName" -Force
}

# Generate Pipe Item Models
$pipeVariants = @("wood", "cobble", "stone", "quartz", "gold", "sandstone", "iron", "diamond", "diamond_wood")
foreach ($pipe in $pipeVariants) {
    if ($pipe -eq "wood") {
        $texBase = "wood_fe_clear"
        $texFilled = "wood_fe_filled"
    } elseif ($pipe -eq "diamond_wood") {
        $texBase = "diamond_wood_fe_clear"
        $texFilled = "diamond_wood_fe_filled"
    } elseif ($pipe -eq "iron") {
        $texBase = "iron_fe_clear"
        $texFilled = "iron_fe_filled"
    } else {
        $texBase = "${pipe}_fe"
        $texFilled = "${pipe}_fe"
    }
    
    $modelContent = @"
{
    "parent": "buildcraftunofficial:item/pipe_item",
    "textures": {
        "top": "buildcraftunofficial:pipes/$texBase",
        "center": "buildcraftunofficial:pipes/$texBase",
        "bottom": "buildcraftunofficial:pipes/$texFilled"
    }
}
"@
    # Naming convention is pipe_$pipe_fe
    $itemName = "pipe_${pipe}_fe"
    if ($pipe -eq "cobble") { $itemName = "pipe_cobble_fe" }
    
    $modelFilePath = "$transportAss\models\item\${itemName}.json"
    $modelContent | Out-File $modelFilePath -Encoding UTF8
}

Write-Host "Assets successfully mapped!"
