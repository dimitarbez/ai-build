# Build .jar with gradle
jar:
	./gradlew clean shadowJar

# Test OpenAI client without Minecraft
test:
	./test.sh