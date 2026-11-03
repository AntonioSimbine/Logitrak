package mz.co.logitrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogitrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(LogitrackApplication.class, args);
	}

}
