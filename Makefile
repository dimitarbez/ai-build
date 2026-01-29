# Build .jar with gradle
jar:
	./gradlew clean shadowJar

# Test OpenAI client without Minecraft
test:
	./test.sh

# Run end-to-end integration tests
test-e2e:
	./test-e2e.sh