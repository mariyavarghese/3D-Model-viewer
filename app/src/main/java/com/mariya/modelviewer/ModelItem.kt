package com.mariya.modelviewer

data class ModelItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val fileName: String,
    val iconEmoji: String,
    val labelCountDescription: String
) {
    companion object {
        val ALL_MODELS = listOf(
            ModelItem(
                id = "bulb",
                title = "Light Bulb",
                subtitle = "Incandescent lamp components",
                fileName = "Bulb.glb",
                iconEmoji = "💡",
                labelCountDescription = "6 Part Labels"
            ),
            ModelItem(
                id = "fiagena",
                title = "Flagellar Motor",
                subtitle = "Bacterial propulsion organelle",
                fileName = "Fiagena.glb",
                iconEmoji = "🦠",
                labelCountDescription = "7 Part Labels"
            ),
            ModelItem(
                id = "lungs",
                title = "Respiratory System",
                subtitle = "Human lungs and bronchial anatomy",
                fileName = "Lungs.glb",
                iconEmoji = "🫁",
                labelCountDescription = "5 Part Labels"
            ),
            ModelItem(
                id = "microscope",
                title = "Optical Microscope",
                subtitle = "Precision lab optics and stage",
                fileName = "Microscope.glb",
                iconEmoji = "🔬",
                labelCountDescription = "12 Part Labels"
            ),
            ModelItem(
                id = "solarsystem",
                title = "Solar System",
                subtitle = "Planetary orbits and celestial bodies",
                fileName = "solarsystem.glb",
                iconEmoji = "🪐",
                labelCountDescription = "9 Part Labels"
            )
        )
    }
}
