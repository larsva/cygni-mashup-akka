package se.cygni.mashup.akka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.web.SpringBootServletInitializer;

/**
 * Created by lasse on 2016-03-07.
 */
@SpringBootApplication
public class AkkaMashupApplication extends SpringBootServletInitializer {


    public static void main(String[] args) throws Exception {
        SpringApplication.run(AkkaMashupApplication.class, args);
    }

}
