import re

with open('app/build.gradle.kts', 'r') as f:
    text = f.read()

# Remove the broken block
text = re.sub(r'tasks\.withType<org\.jetbrains\.kotlin\.gradle\.tasks\.KotlinCompile>\(\)\.configureEach \{\s*kotlinOptions \{\s*freeCompilerArgs \+= listOf\([^)]+\)\s*\}\s*\}', '', text)

# Add the correct configuration at the end of the file
new_config = """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"))
    }
}
"""

with open('app/build.gradle.kts', 'w') as f:
    f.write(text + new_config)

