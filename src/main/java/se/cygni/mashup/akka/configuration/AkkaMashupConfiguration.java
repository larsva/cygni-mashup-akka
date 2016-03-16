package se.cygni.mashup.akka.configuration;


import akka.actor.ActorSystem;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.ws.rs.ApplicationPath;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static se.cygni.mashup.akka.extension.SpringExtension.SpringExtProvider;

/**
 * Created by lasse on 2016-03-07.
 */
@Configuration
@ApplicationPath("/api")
public class AkkaMashupConfiguration extends ResourceConfig {

    // the application context is needed to initialize the Akka Spring Extension
    @Autowired
    private ApplicationContext applicationContext;

    public AkkaMashupConfiguration() {
        this.packages("se.cygni.mashup.akka.resources");
    }

    /**
     * Actor system singleton for this application.
     */
    @Bean
    public ActorSystem actorSystem() {
        ActorSystem system = ActorSystem.create("Cygni-Mashup");
        SpringExtProvider.get(system).initialize(applicationContext);
        return system;
    }

    @Bean(name="restTemplate")
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    Executor executor() {
        return Executors.newFixedThreadPool(20);
    }
}
