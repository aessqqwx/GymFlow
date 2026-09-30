plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDirs("../app/src/main/java/com/aess/gymflow", "../tools/pure-tests")
        kotlin.include(
            "Models.kt", "BirthDate.kt", "WorkoutData.kt", "WorkoutProgression.kt",
            "WorkoutHistoryLogic.kt", "WorkoutRecordUpdater.kt", "PersonalizedWorkouts.kt",
            "WorkoutStats.kt", "Stubs.kt", "Main.kt"
        )
    }
}
application { mainClass.set("com.aess.gymflow.MainKt") }
