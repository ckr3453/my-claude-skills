#!/usr/bin/env python3
"""
Entity Generator for Spring Boot Kotlin Projects

Generates JPA entity classes following the project conventions:
- Value Object pattern for embedded types
- Companion object factory methods
- BaseEntity inheritance
- Proper annotations
"""

import sys
import os
from pathlib import Path

def to_camel_case(snake_str: str) -> str:
    """Convert snake_case to CamelCase"""
    components = snake_str.split('_')
    return ''.join(x.title() for x in components)

def to_snake_case(camel_str: str) -> str:
    """Convert CamelCase to snake_case"""
    import re
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', camel_str)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()

def generate_entity(entity_name: str, package_name: str, fields: list) -> str:
    """
    Generate entity class code
    
    Args:
        entity_name: Name of the entity (e.g., 'Member')
        package_name: Base package name (e.g., 'com.example.demo')
        fields: List of (field_name, field_type, nullable) tuples
    
    Returns:
        Generated Kotlin code as string
    """
    table_name = to_snake_case(entity_name)
    
    template = f"""package {package_name}.domain

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import {package_name}.domain.base.BaseEntity

/**
 * {entity_name} Entity
 * Table: {table_name}
 */
@Entity(name = "{table_name}")
class {entity_name}(
    @Id @Tsid
    @Column(name = "{table_name}_id", length = 13)
    var id: String? = null,
    
"""
    
    # Add fields
    for field_name, field_type, nullable in fields:
        null_annotation = "" if nullable else "nullable = false, "
        null_marker = "?" if nullable else ""
        
        template += f"""    @Column({null_annotation}length = 255)
    var {field_name}: {field_type}{null_marker},
    
"""
    
    template += """): BaseEntity() {
    
    companion object {
        fun of(/* Add parameters */): ENTITY_NAME {
            return ENTITY_NAME(
                // Initialize fields
            )
        }
    }
}
""".replace("ENTITY_NAME", entity_name)
    
    return template

def main():
    if len(sys.argv) < 4:
        print("Usage: generate_entity.py <entity_name> <package_name> <output_dir>")
        print("Example: generate_entity.py Member com.example.demo src/main/kotlin")
        sys.exit(1)
    
    entity_name = sys.argv[1]
    package_name = sys.argv[2]
    output_dir = sys.argv[3]
    
    # Example fields (in real usage, these would be provided or prompted)
    fields = [
        ("name", "String", False),
        ("email", "String", False),
        ("phoneNumber", "String", True),
    ]
    
    code = generate_entity(entity_name, package_name, fields)
    
    # Create output directory
    package_path = package_name.replace('.', '/')
    domain_dir = Path(output_dir) / package_path / "domain"
    domain_dir.mkdir(parents=True, exist_ok=True)
    
    # Write file
    output_file = domain_dir / f"{entity_name}.kt"
    output_file.write_text(code)
    
    print(f"✅ Generated entity: {output_file}")
    print(f"📝 Remember to:")
    print(f"   1. Add proper field types and constraints")
    print(f"   2. Implement factory method in companion object")
    print(f"   3. Create corresponding Repository interface")
    print(f"   4. Generate Service and Controller if needed")

if __name__ == "__main__":
    main()
