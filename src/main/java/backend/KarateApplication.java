package backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("backend.config")
public class KarateApplication {

	public static void main(String[] args) {
		// Load .env from the working directory into System properties BEFORE
		// Spring reads application.yaml. Spring's ${DB_URL:default} placeholders
		// pick them up. Safe in production: file not present → no-op.
		Dotenv.configure().ignoreIfMissing().load().entries().forEach(e ->
				System.setProperty(e.getKey(), e.getValue()));
		SpringApplication.run(KarateApplication.class, args);
	}

}
