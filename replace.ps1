Get-ChildItem -Path "forge\src\main\java" -File -Recurse | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $content = $content.Replace("com.gbdhapa.neoforge", "com.gbdhapa.forge")
    $content = $content.Replace("LibrarianfilterNeoForge", "LibrarianfilterForge")
    $content = $content.Replace("NeoForgeTradeConfigScreen", "ForgeTradeConfigScreen")
    $content = $content.Replace("net.neoforged.api.distmarker.Dist", "net.minecraftforge.api.distmarker.Dist")
    $content = $content.Replace("net.neoforged.bus.api.IEventBus", "net.minecraftforge.eventbus.api.IEventBus")
    $content = $content.Replace("net.neoforged.fml.ModContainer", "net.minecraftforge.fml.ModContainer")
    $content = $content.Replace("net.neoforged.fml.common.Mod", "net.minecraftforge.fml.common.Mod")
    $content = $content.Replace("net.neoforged.neoforge.common.NeoForge", "net.minecraftforge.common.MinecraftForge")
    $content = $content.Replace("NeoForge.EVENT_BUS", "MinecraftForge.EVENT_BUS")
    $content = $content.Replace("net.neoforged.neoforge.event.", "net.minecraftforge.event.")
    $content = $content.Replace("net.neoforged.neoforge.client.event.", "net.minecraftforge.client.event.")
    $content = $content.Replace("net.neoforged.neoforge.network.", "net.minecraftforge.network.")
    Set-Content -Path $_.FullName -Value $content
}
