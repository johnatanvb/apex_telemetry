package com.example.telemetry.data

import com.example.telemetry.model.CarCategory
import com.example.telemetry.model.GameTitle
import com.example.telemetry.model.VehicleCapabilities

object GameCatalog {

    val selectableGames: List<GameTitle> = GameTitle.entries

    val concreteGames: List<GameTitle> = GameTitle.entries.filter { it != GameTitle.AUTO }

    fun capabilitiesFor(game: GameTitle): VehicleCapabilities = when (game) {
        GameTitle.AUTO,
        GameTitle.F1_25,
        GameTitle.F1_24,
        GameTitle.F1_23 -> VehicleCapabilities(
            hasErs = true,
            hasDrs = true,
            hasTractionControlAbs = false,
            hasTurboBoost = false,
            hasSuspensionTravel = false,
            hasTireSlipRatio = false,
            hasOpponentGrid = true,
            hasPitStrategy = true,
            hasEngineOilWater = false,
            hasBrakeTemps = true,
            hasAeroDamage = true,
            hasPowerUnitWear = true,
        )

        GameTitle.GRAN_TURISMO_7 -> VehicleCapabilities(
            hasErs = false,
            hasDrs = false,
            hasTractionControlAbs = false,
            hasTurboBoost = true,
            hasSuspensionTravel = true,
            hasTireSlipRatio = true,
            hasOpponentGrid = false,
            hasPitStrategy = false,
            hasEngineOilWater = true,
            hasBrakeTemps = false,
            hasAeroDamage = false,
            hasPowerUnitWear = false,
        )

        GameTitle.FORZA_MOTORSPORT -> VehicleCapabilities(
            hasErs = false,
            hasDrs = false,
            hasTractionControlAbs = false,
            hasTurboBoost = true,
            hasSuspensionTravel = true,
            hasTireSlipRatio = true,
            hasOpponentGrid = false,
            hasPitStrategy = false,
            hasEngineOilWater = false,
            hasBrakeTemps = false,
            hasAeroDamage = false,
            hasPowerUnitWear = false,
        )

        GameTitle.FORZA_HORIZON_5 -> VehicleCapabilities(
            hasErs = false,
            hasDrs = false,
            hasTractionControlAbs = false,
            hasTurboBoost = true,
            hasSuspensionTravel = true,
            hasTireSlipRatio = true,
            hasOpponentGrid = false,
            hasPitStrategy = false,
            hasEngineOilWater = false,
            hasBrakeTemps = false,
            hasAeroDamage = false,
            hasPowerUnitWear = false,
        )

        GameTitle.ASSETTO_CORSA -> VehicleCapabilities(
            hasErs = false,
            hasDrs = false,
            hasTractionControlAbs = true,
            hasTurboBoost = true,
            hasSuspensionTravel = true,
            hasTireSlipRatio = true,
            hasOpponentGrid = false,
            hasPitStrategy = false,
            hasEngineOilWater = false,
            hasBrakeTemps = false,
            hasAeroDamage = false,
            hasPowerUnitWear = false,
        )

        GameTitle.ASSETTO_CORSA_COMPETIZIONE -> VehicleCapabilities(
            hasErs = false,
            hasDrs = false,
            hasTractionControlAbs = true,
            hasTurboBoost = false,
            hasSuspensionTravel = true,
            hasTireSlipRatio = true,
            hasOpponentGrid = true,
            hasPitStrategy = true,
            hasEngineOilWater = false,
            hasBrakeTemps = true,
            hasAeroDamage = false,
            hasPowerUnitWear = false,
        )

        GameTitle.PROJECT_CARS_2,
        GameTitle.AUTOMOBILISTA_2 -> VehicleCapabilities(
            hasErs = false,
            hasDrs = false,
            hasTractionControlAbs = true,
            hasTurboBoost = true,
            hasSuspensionTravel = true,
            hasTireSlipRatio = false,
            hasOpponentGrid = true,
            hasPitStrategy = true,
            hasEngineOilWater = true,
            hasBrakeTemps = true,
            hasAeroDamage = true,
            hasPowerUnitWear = false,
        )
    }

    fun requiresHostIpHandshake(game: GameTitle): Boolean = when (game) {
        GameTitle.GRAN_TURISMO_7,
        GameTitle.ASSETTO_CORSA,
        GameTitle.ASSETTO_CORSA_COMPETIZIONE -> true
        else -> false
    }

    fun setupInstructionsFor(game: GameTitle, localIp: String, customPort: Int): String = when (game) {
        GameTitle.AUTO ->
            "Modo Automático escuta F1 23/24/25 (20777), Forza (5300), PC2/AMS2 (5606) e GT7 (33740) ao mesmo tempo. Informe o IP do PS/PC abaixo caso use GT7 ou Assetto Corsa."

        GameTitle.F1_25,
        GameTitle.F1_24,
        GameTitle.F1_23 ->
            "No jogo (${game.displayName}): Configurações > Telemetria > Telemetria UDP Ligada, IP $localIp, Porta $customPort, Taxa 60Hz, Formato ${game.shortTag.removePrefix("F1 ")}."

        GameTitle.GRAN_TURISMO_7 ->
            "GT7 envia UDP criptografado (Salsa20) na porta 33740. Informe o IP do seu PlayStation acima para enviar o heartbeat na porta 33739."

        GameTitle.FORZA_MOTORSPORT ->
            "No Forza Motorsport: Configurações > Jogabilidade/HUD > Saída de Dados (Data Out) Ligada, IP $localIp, Porta $customPort (padrão 5300)."

        GameTitle.FORZA_HORIZON_5 ->
            "No Forza Horizon 5: HUD e Jogabilidade > Saída de Dados Ligada, IP $localIp, Porta $customPort (padrão 5300, layout Horizon)."

        GameTitle.ASSETTO_CORSA ->
            "No Assetto Corsa (PC): Informe o IP do PC acima; o app envia handshake na porta 9996 e recebe RTCarInfo (328 bytes) em tempo real."

        GameTitle.ASSETTO_CORSA_COMPETIZIONE ->
            "No ACC (PC): Ative Broadcasting em Documents/ACC/Config/broadcasting.json (porta $customPort, senha 'asd') e informe o IP do PC acima."

        GameTitle.PROJECT_CARS_2,
        GameTitle.AUTOMOBILISTA_2 ->
            "No ${game.displayName}: Opções > Sistema > Memória Compartilhada/UDP = Project CARS 2 (Frequência 5+), Porta $customPort (padrão 5606)."
    }

    fun categoryLabelFor(category: CarCategory): String = category.label
}
