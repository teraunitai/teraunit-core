package ai.teraunit.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Industrial Move: Exclusions removed. Database auto-wire is now active.
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TeraunitCoreApplication {
	public static void main(String[] args) {
		SpringApplication.run(TeraunitCoreApplication.class, args);
	}
}
