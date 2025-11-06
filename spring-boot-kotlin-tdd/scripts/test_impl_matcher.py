#!/usr/bin/env python3
"""
Test-Implementation Matcher

Validates that tests and implementation match perfectly.
Checks for:
- Method signature mismatches
- Missing methods in implementation
- Missing test cases for implemented methods
"""

import re
from typing import Set, Dict, Tuple, List
from pathlib import Path


class TestImplMatcher:
    """Validates test and implementation consistency."""
    
    def __init__(self, test_code: str, impl_code: str):
        self.test_code = test_code
        self.impl_code = impl_code
        self.errors: List[str] = []
        self.warnings: List[str] = []
    
    def extract_method_calls(self) -> Dict[str, Set[str]]:
        """Extract method calls from test code by class."""
        calls_by_class = {}
        
        # Find service/repo calls
        # Pattern: service.methodName(...) or repository.methodName(...)
        pattern = r'(\w+)\.(\w+)\s*\('
        matches = re.finditer(pattern, self.test_code)
        
        for match in matches:
            obj = match.group(1)
            method = match.group(2)
            
            if obj not in calls_by_class:
                calls_by_class[obj] = set()
            calls_by_class[obj].add(method)
        
        return calls_by_class
    
    def extract_method_defs(self) -> Set[str]:
        """Extract method definitions from implementation code."""
        defs = set()
        
        # Pattern: fun methodName(...) or def methodName(...)
        pattern = r'fun\s+(\w+)\s*\('
        matches = re.finditer(pattern, self.impl_code)
        
        for match in matches:
            defs.add(match.group(1))
        
        return defs
    
    def extract_test_methods(self) -> Set[str]:
        """Extract test method names from test code."""
        tests = set()
        
        # Pattern: fun testName(...) or @Test fun method()
        pattern = r'fun\s+((?:test|should|when)\w*)\s*\('
        matches = re.finditer(pattern, self.test_code)
        
        for match in matches:
            tests.add(match.group(1))
        
        return tests
    
    def extract_entities_from_tests(self) -> Set[str]:
        """Extract entity classes being tested."""
        entities = set()
        
        # Pattern: @Test class ..Test or when testing ...Service
        pattern = r'class\s+(\w+)(?:ServiceTest|RepositoryTest|ControllerTest)'
        matches = re.finditer(pattern, self.test_code)
        
        for match in matches:
            entity = match.group(1)
            entities.add(entity)
        
        return entities
    
    def validate_method_match(self) -> bool:
        """Validate that all tested methods exist in implementation."""
        calls = self.extract_method_calls()
        defs = self.extract_method_defs()
        
        for obj, methods in calls.items():
            for method in methods:
                # Skip common helpers
                if method in ['assertEquals', 'assertTrue', 'assertFalse', 'reset', 'given', 'verify']:
                    continue
                
                if method not in defs:
                    self.errors.append(
                        f"Method '{method}' is called in tests but not found in implementation"
                    )
        
        return len(self.errors) == 0
    
    def validate_test_coverage(self) -> bool:
        """Check if all public methods have tests."""
        defs = self.extract_method_defs()
        calls = self.extract_method_calls()
        
        all_called_methods = set()
        for methods in calls.values():
            all_called_methods.update(methods)
        
        # Skip private and helper methods
        public_methods = {m for m in defs if not m.startswith('_')}
        
        uncovered = public_methods - all_called_methods
        uncovered = {m for m in uncovered if m not in [
            'equals', 'hashCode', 'toString', 'getClass'
        ]}
        
        if uncovered:
            self.warnings.append(
                f"Public methods without tests: {uncovered}"
            )
        
        return True
    
    def validate_exception_handling(self) -> bool:
        """Check that thrown exceptions are tested."""
        # Find throws declarations
        throws_pattern = r'throws\s+(\w+(?:\s*,\s*\w+)*)'
        throws_matches = re.finditer(throws_pattern, self.impl_code)
        
        thrown_exceptions = set()
        for match in throws_matches:
            exc = match.group(1)
            thrown_exceptions.update(exc.replace(' ', '').split(','))
        
        # Find exception tests
        test_pattern = r'assertThatThrownBy.*?\.isInstanceOf\((\w+)'
        test_matches = re.finditer(test_pattern, self.test_code, re.DOTALL)
        
        tested_exceptions = set()
        for match in test_matches:
            tested_exceptions.add(match.group(1))
        
        untested = thrown_exceptions - tested_exceptions
        if untested:
            self.warnings.append(
                f"Exceptions declared but not tested: {untested}"
            )
        
        return True
    
    def validate_signature_match(self) -> bool:
        """Check method signatures match between test and implementation."""
        # Extract method signatures from both
        impl_pattern = r'fun\s+(\w+)\s*\((.*?)\)'
        test_pattern = r'\.(\w+)\s*\((.*?)\)'
        
        impl_sigs = {}
        for match in re.finditer(impl_pattern, self.impl_code):
            impl_sigs[match.group(1)] = match.group(2)
        
        test_calls = {}
        for match in re.finditer(test_pattern, self.test_code):
            test_calls[match.group(1)] = match.group(2)
        
        # Check for parameter count mismatches
        for method, test_params in test_calls.items():
            if method in impl_sigs:
                impl_params = impl_sigs[method]
                test_param_count = len([p for p in test_params.split(',') if p.strip()])
                impl_param_count = len([p for p in impl_params.split(',') if p.strip()])
                
                if test_param_count != impl_param_count:
                    self.errors.append(
                        f"Method '{method}' called with {test_param_count} params in test, "
                        f"but defined with {impl_param_count} in implementation"
                    )
        
        return len(self.errors) == 0
    
    def run_all_checks(self) -> Tuple[bool, Dict[str, any]]:
        """Run all validation checks."""
        self.validate_method_match()
        self.validate_test_coverage()
        self.validate_exception_handling()
        self.validate_signature_match()
        
        success = len(self.errors) == 0
        
        return success, {
            'success': success,
            'errors': self.errors,
            'warnings': self.warnings,
            'error_count': len(self.errors),
            'warning_count': len(self.warnings)
        }


def validate_files(test_file: Path, impl_file: Path) -> Dict:
    """Validate test and implementation files."""
    try:
        test_code = test_file.read_text()
        impl_code = impl_file.read_text()
    except FileNotFoundError as e:
        return {
            'success': False,
            'error': f"File not found: {e}"
        }
    
    matcher = TestImplMatcher(test_code, impl_code)
    return matcher.run_all_checks()[1]


def print_report(result: Dict):
    """Print validation report."""
    print("\n" + "="*60)
    print("TEST-IMPLEMENTATION MATCHER REPORT")
    print("="*60)
    
    if result['success']:
        print("✅ PASSED: All tests match implementation")
    else:
        print("❌ FAILED: Found mismatches")
    
    if result.get('errors'):
        print(f"\n🔴 ERRORS ({len(result['errors'])}):")
        for error in result['errors']:
            print(f"  - {error}")
    
    if result.get('warnings'):
        print(f"\n⚠️  WARNINGS ({len(result['warnings'])}):")
        for warning in result['warnings']:
            print(f"  - {warning}")
    
    print("\n" + "="*60)
    print(f"Total: {result['error_count']} errors, {result['warning_count']} warnings")
    print("="*60 + "\n")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) < 3:
        print("Usage: python test_impl_matcher.py <test_file> <impl_file>")
        sys.exit(1)
    
    test_file = Path(sys.argv[1])
    impl_file = Path(sys.argv[2])
    
    result = validate_files(test_file, impl_file)
    print_report(result)
    
    sys.exit(0 if result['success'] else 1)
