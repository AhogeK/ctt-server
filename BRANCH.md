# Scaffold Branch: Unified Conventions

**Branch**: `scaffold/unified-conventions`
**Purpose**: Establish engineering standards for long-term maintainability
**Scope**: Convention files and tooling only (NO business logic)

---

## What's Added

### 1. Editor Configuration (`.editorconfig`)
- Universal editor settings
- 4-space indentation for Java/Kotlin
- 2-space indentation for XML/JSON/YAML
- Max line length: 120
- UTF-8 encoding, LF line endings

### 2. Development Conventions (`CONVENTIONS.md`)
Comprehensive guide covering:
- Git workflow (branch naming, commit messages)
- Code style (naming, imports, comments)
- Testing standards (AAA pattern, coverage)
- API documentation (OpenAPI)
- Database conventions (naming, required columns)
- Security guidelines
- Performance best practices
- Code review checklist

### 3. Git Commit Template (`.gitmessage`)
- Conventional Commits format
- Type and scope guidelines
- Body and footer examples
- References to tickets/issues

### 4. Code Quality Tools

#### Spotless (Auto-formatting)
```bash
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Fix formatting
```

#### JaCoCo (Test Coverage)
```bash
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

### 5. Gradle Configuration Updates
- Added Spotless plugin for code formatting
- Added JaCoCo for test coverage reporting
- Google Java Format (1.22.0)
- Import ordering rules

---

## Usage

### Setup (One-time)

```bash
# Configure Git commit template
git config commit.template .gitmessage

# Verify tools work
./gradlew spotlessCheck
./gradlew test jacocoTestReport
```

### Daily Development

1. **Before committing**:
   ```bash
   ./gradlew spotlessApply  # Fix formatting
   ./gradlew check          # Run all checks
   ```

2. **Writing commit messages**:
   ```bash
   git commit  # Opens template in editor
   ```

3. **Code review checklist**:
   - See `CONVENTIONS.md` Section 8

---

## Integration Path

This branch establishes conventions. To use in feature development:

```bash
# Merge conventions into main branch
git checkout main
git merge scaffold/unified-conventions

# Then create feature branches
git checkout -b feat/CTT-xxx-some-feature
```

---

## Future Enhancements (TODO)

- [ ] Add Checkstyle configuration
- [ ] Add SpotBugs for static analysis
- [ ] Configure pre-commit hooks
- [ ] Add IDE code style files (IntelliJ, VS Code)
- [ ] Set up CI/CD pipeline configuration
- [ ] Add architecture decision records (ADR)

---

**Created**: 2026-03-03
**Author**: AI Assistant
**Status**: Active scaffold branch
