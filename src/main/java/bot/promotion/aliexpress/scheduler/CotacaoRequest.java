package bot.promotion.aliexpress.scheduler;

import bot.promotion.aliexpress.dto.BCBApiResponse;
import bot.promotion.telegram.service.NotificationService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CotacaoRequest {
    private final RestTemplate restTemplate;
    private final AtomicReference<BigDecimal> cachedCotacao = new AtomicReference<>();
    private final NotificationService notify;
    private final Clock clock;

    @Autowired
    public CotacaoRequest(RestTemplate restTemplate, NotificationService notify, Clock clock) {
        this.restTemplate = restTemplate;
        this.notify = notify;
        this.clock = clock;
    }

    public BigDecimal getCachedCotacao() {
        return cachedCotacao.get();
    }

    @PostConstruct
    public void initializeCotacao() {
        updateCotacao();
    }

    @Scheduled(cron = "0 0 11 * * MON-FRI", zone = "America/Sao_Paulo")
    public void updateCotacao() {
        try {
            String dataFormat = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
            String bcbApiUrl = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaAberturaOuIntermediario(codigoMoeda=@codigoMoeda,dataCotacao=@dataCotacao)?@codigoMoeda='USD'&@dataCotacao='%s'&$format=json&$select=cotacaoVenda";
            String urlFinal = String.format(bcbApiUrl, dataFormat);

            BCBApiResponse response = restTemplate.getForObject(urlFinal, BCBApiResponse.class);

            if (response == null || response.getValue().getFirst().getCotacao() == null) {
                notify.sendWarningMessage("Response from BCB API is null or empty in line 51");
                return;
            }
            Double novaCotacao = response.getValue().getFirst().getCotacao();
            BigDecimal cotacaoDecimal = BigDecimal.valueOf(novaCotacao);
            BigDecimal cotacaoRounded = cotacaoDecimal.setScale(2, RoundingMode.HALF_UP);
            cachedCotacao.set(cotacaoRounded);
        } catch (Exception e) {
            notify.sendErrorMessage("Error fetching cotacao from BCB API in line 59: ", e);
        }
    }
}

