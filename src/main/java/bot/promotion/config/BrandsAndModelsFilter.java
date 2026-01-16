package bot.promotion.config;

import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
public class BrandsAndModelsFilter {
    @Bean
    public List<BrandAndModel> getBrandsAndModels() {
        return List.of(
                new BrandAndModel("delux", List.of("m900", "m800"), List.of("receptor", "receiver")),
                new BrandAndModel("netac", List.of("sata"), List.of()),
                new BrandAndModel("aula", List.of("f87", "f75", "hero", "win", "ag", "sc680", "mini"), List.of("keycap", "conjunto", "dungle", "estabilizador")),
                new BrandAndModel("arzopa", List.of("arzopa"), List.of("protetora", "protetor", "filme")),
                new BrandAndModel("attack shark", List.of("x6", "x11", "x87"), List.of()),
                new BrandAndModel("movespeed", List.of(), List.of()),
                new BrandAndModel("kootion", List.of("5000", "7400", "7200", "3500", "sata"), List.of()),
                new BrandAndModel("mchose", List.of(), List.of()),
                new BrandAndModel("qcy", List.of("h3"), List.of()),
                new BrandAndModel("8bitdo", List.of(), List.of()),
                new BrandAndModel("rapoo", List.of("vt3"), List.of()),
                new BrandAndModel("epomaker", List.of(), List.of()),
                new BrandAndModel("akko", List.of(), List.of()),
                new BrandAndModel("teucer", List.of(), List.of()),
                new BrandAndModel("fifine", List.of(), List.of()),
                new BrandAndModel("baseus", List.of(), List.of()),
                new BrandAndModel("easysmx", List.of(), List.of()),
                new BrandAndModel("deepcool", List.of(), List.of()),
                new BrandAndModel("binnune", List.of(), List.of()),
                new BrandAndModel("machenike", List.of("g5", "l8"), List.of()),
                new BrandAndModel("magcubic", List.of(), List.of())
        );
    }
}
