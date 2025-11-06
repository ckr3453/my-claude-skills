#!/bin/bash
# init_structure.sh - Initialize Spring Boot Kotlin project directory structure
# Usage: bash init_structure.sh <project-name> <base-package>
# Example: bash init_structure.sh my-app com.example.myapp

set -e

if [ $# -lt 2 ]; then
    echo "Usage: bash init_structure.sh <project-name> <base-package>"
    echo "Example: bash init_structure.sh my-app com.example.myapp"
    exit 1
fi

PROJECT_NAME=$1
BASE_PACKAGE=$2
PACKAGE_PATH=$(echo $BASE_PACKAGE | tr '.' '/')

echo "🚀 Initializing Spring Boot Kotlin project: $PROJECT_NAME"
echo "📦 Base package: $BASE_PACKAGE"

# Create root directory
mkdir -p "$PROJECT_NAME"
cd "$PROJECT_NAME"

# Create source directories
mkdir -p "src/main/kotlin/$PACKAGE_PATH"/{domain/base,dto/{request,response},repository/impl,service,controller,config,exception/base,filter,aop,util}
mkdir -p "src/main/resources"
mkdir -p "src/test/kotlin/$PACKAGE_PATH"
mkdir -p "src/test/resources"

# Create gradle wrapper directory
mkdir -p "gradle/wrapper"

echo "✅ Directory structure created successfully!"
echo ""
echo "📁 Project structure:"
tree -L 4 -I 'build|.gradle|.idea' .

echo ""
echo "📝 Next steps:"
echo "1. Copy build.gradle.kts and settings.gradle.kts"
echo "2. Copy gradle wrapper files"
echo "3. Generate Application.kt main class"
echo "4. Create domain entities"
echo "5. Set up security configuration"
