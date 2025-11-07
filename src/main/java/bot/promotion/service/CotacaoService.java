package bot.promotion.service;

import bot.promotion.dto.BCBApiResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CotacaoService {
    private final RestTemplate restTemplate;
    private final AtomicReference<Double> cachedCotacao = new AtomicReference<>();

    @Autowired
    public CotacaoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Double getCachedCotacao() {
        return cachedCotacao.get();
    }

    @PostConstruct
    public void initializeCotacao() {
        updateCotacao();
    }

    @Scheduled(cron = "0 0 11 * * MON-FRI", zone = "America/Sao_Paulo")
    public void updateCotacao() {
        try {
            String dataFormat = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
            String bcbApiUrl = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaAberturaOuIntermediario(codigoMoeda=@codigoMoeda,dataCotacao=@dataCotacao)?@codigoMoeda='USD'&@dataCotacao='%s'&$format=json&$select=cotacaoVenda";
            String urlFinal = String.format(bcbApiUrl, dataFormat);

            BCBApiResponse response = restTemplate.getForObject(urlFinal, BCBApiResponse.class);

            if (response == null || response.getValue() == null) {
                System.out.println("Response from BCB API is null or empty in line 44");
                return;
            }
            Double novaCotacao = response.getValue().getFirst().getCotacao();
            BigDecimal cotacaoDecimal = BigDecimal.valueOf(novaCotacao);
            BigDecimal cotacaoRounded = cotacaoDecimal.setScale(2, RoundingMode.HALF_UP);
            cachedCotacao.set(cotacaoRounded.doubleValue());
        } catch (Exception e) {
            System.out.println("Error fetching cotacao from BCB API in line 52: " + e.getMessage());
        }
    }
}

