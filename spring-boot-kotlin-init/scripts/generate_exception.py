#!/usr/bin/env python3
"""
Exception Generator for Spring Boot Kotlin Projects

Generates domain-specific exception classes following project conventions:
- Extends base BusinessException
- Includes ErrorCode enum
- Proper package organization
"""

import sys
from pathlib import Path

def generate_exception(exception_name: str, package_name: str, message: str, error_code: str) -> str:
    """Generate custom exception class"""
    
    template = f"""package {package_name}.exception

import {package_name}.domain.ErrorCode
import {package_name}.exception.base.BusinessException

/**
 * {exception_name}
 * 
 * Thrown when: {message}
 */
class {exception_name} : BusinessException(
    message = "{message}",
    errorCode = ErrorCode.{error_code}
)
"""
    return template

def generate_base_exception(package_name: str) -> str:
    """Generate base BusinessException class"""
    
    template = f"""package {package_name}.exception.base

import {package_name}.domain.ErrorCode

/**
 * Base Exception for all business logic exceptions
 */
sealed class BusinessException(
    message: String,
    val errorCode: ErrorCode
) : RuntimeException(message)
"""
    return template

def generate_error_code_enum(package_name: str) -> str:
    """Generate ErrorCode enum"""
    
    template = f"""package {package_name}.domain

/**
 * Error codes for exception handling
 */
enum class ErrorCode(val code: String, val description: String) {{
    // Common
    INVALID_INPUT("E001", "Invalid input parameter"),
    RESOURCE_NOT_FOUND("E002", "Resource not found"),
    UNAUTHORIZED("E003", "Unauthorized access"),
    FORBIDDEN("E004", "Forbidden access"),
    
    // Domain-specific (add as needed)
    MEMBER_NOT_FOUND("M001", "Member not found"),
    MEMBER_EMAIL_ALREADY_EXISTS("M002", "Member email already exists"),
    INVALID_PASSWORD("M003", "Invalid password format"),
    
    // Add more error codes here
    ;
}}
"""
    return template

def main():
    if len(sys.argv) < 5:
        print("Usage: generate_exception.py <exception_name> <package_name> <message> <error_code> <output_dir>")
        print("Example: generate_exception.py MemberNotFoundException com.example.demo 'Member not found' MEMBER_NOT_FOUND src/main/kotlin")
        sys.exit(1)
    
    exception_name = sys.argv[1]
    package_name = sys.argv[2]
    message = sys.argv[3]
    error_code = sys.argv[4]
    output_dir = sys.argv[5] if len(sys.argv) > 5 else "src/main/kotlin"
    
    # Generate exception
    code = generate_exception(exception_name, package_name, message, error_code)
    
    # Create output directory
    package_path = package_name.replace('.', '/')
    exception_dir = Path(output_dir) / package_path / "exception"
    exception_dir.mkdir(parents=True, exist_ok=True)
    
    # Write exception file
    output_file = exception_dir / f"{exception_name}.kt"
    output_file.write_text(code)
    print(f"✅ Generated exception: {output_file}")
    
    # Check if base exception exists, if not create it
    base_dir = exception_dir / "base"
    base_dir.mkdir(exist_ok=True)
    base_exception_file = base_dir / "BusinessException.kt"
    
    if not base_exception_file.exists():
        base_code = generate_base_exception(package_name)
        base_exception_file.write_text(base_code)
        print(f"✅ Generated base exception: {base_exception_file}")
    
    # Check if ErrorCode exists
    domain_dir = Path(output_dir) / package_path / "domain"
    domain_dir.mkdir(parents=True, exist_ok=True)
    error_code_file = domain_dir / "ErrorCode.kt"
    
    if not error_code_file.exists():
        error_code_code = generate_error_code_enum(package_name)
        error_code_file.write_text(error_code_code)
        print(f"✅ Generated ErrorCode enum: {error_code_file}")
    else:
        print(f"⚠️  ErrorCode enum already exists. Add '{error_code}' manually if needed.")

if __name__ == "__main__":
    main()
