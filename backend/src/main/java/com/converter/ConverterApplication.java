package com.converter;

import com.converter.config.props.AbuseProtectionProperties;
import com.converter.config.props.AdminSeedProperties;
import com.converter.config.props.CorsProperties;
import com.converter.config.props.GoogleAuthProperties;
import com.converter.config.props.JwtProperties;
import com.converter.config.props.LoginProtectionProperties;
import com.converter.config.props.PushProperties;
import com.converter.config.props.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        JwtProperties.class,
        CorsProperties.class,
        AdminSeedProperties.class,
        LoginProtectionProperties.class,
        AbuseProtectionProperties.class,
        StorageProperties.class,
        GoogleAuthProperties.class,
        PushProperties.class
})
public class ConverterApplication {

    public static void main(String[] args) {
        // Toute la couche metier raisonne en UTC. Forcer le fuseau de la JVM
        // evite qu'un serveur mal configure decale les horodatages financiers
        // et, surtout, les fenetres de verrouillage de taux.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ConverterApplication.class, args);
    }
}
