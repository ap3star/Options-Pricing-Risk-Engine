package com.optionsengine.api;

import com.optionsengine.api.Dtos.GreeksResponse;
import com.optionsengine.api.Dtos.ImpliedVolRequest;
import com.optionsengine.api.Dtos.ImpliedVolResponse;
import com.optionsengine.api.Dtos.McSettings;
import com.optionsengine.api.Dtos.Model;
import com.optionsengine.api.Dtos.PositionRequest;
import com.optionsengine.api.Dtos.PriceRequest;
import com.optionsengine.api.Dtos.PriceResponse;
import com.optionsengine.api.Dtos.VarRequest;
import com.optionsengine.model.Greeks;
import com.optionsengine.model.Option;
import com.optionsengine.pricing.BlackScholes;
import com.optionsengine.pricing.ImpliedVolResult;
import com.optionsengine.pricing.ImpliedVolSolver;
import com.optionsengine.pricing.McResult;
import com.optionsengine.pricing.MonteCarloPricer;
import com.optionsengine.risk.HistoricalVar;
import com.optionsengine.risk.OptionPosition;
import com.optionsengine.risk.Position;
import com.optionsengine.risk.StockPosition;
import com.optionsengine.risk.VarResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PricingController {

    private final MonteCarloPricer monteCarlo;

    public PricingController(MonteCarloPricer monteCarlo) {
        this.monteCarlo = monteCarlo;
    }

    @PostMapping("/price")
    public PriceResponse price(@Valid @RequestBody PriceRequest request) {
        Option option = request.option().toOption();
        if (request.modelOrDefault() == Model.BLACK_SCHOLES) {
            return new PriceResponse(Model.BLACK_SCHOLES, BlackScholes.price(option), null, null);
        }
        McSettings mc = request.mcOrDefault();
        McResult r = monteCarlo.price(option, mc.pathsOrDefault(), mc.seedOrDefault(), mc.antitheticOrDefault());
        return new PriceResponse(Model.MONTE_CARLO, r.price(), r.stdError(), r.paths());
    }

    @PostMapping("/greeks")
    public GreeksResponse greeks(@Valid @RequestBody PriceRequest request) {
        Option option = request.option().toOption();
        if (request.modelOrDefault() == Model.BLACK_SCHOLES) {
            return new GreeksResponse(Model.BLACK_SCHOLES, BlackScholes.greeks(option));
        }
        McSettings mc = request.mcOrDefault();
        Greeks g = monteCarlo.greeks(option, mc.pathsOrDefault(), mc.seedOrDefault(), mc.antitheticOrDefault());
        return new GreeksResponse(Model.MONTE_CARLO, g);
    }

    @PostMapping("/implied-vol")
    public ImpliedVolResponse impliedVol(@Valid @RequestBody ImpliedVolRequest r) {
        ImpliedVolResult res = ImpliedVolSolver.solve(
                r.marketPrice(), r.spot(), r.strike(), r.rate(), r.dividendYield(), r.expiry(), r.type());
        return new ImpliedVolResponse(res.volatility(), res.iterations(), res.bisectionSteps());
    }

    @PostMapping("/var")
    public VarResult var(@Valid @RequestBody VarRequest request) {
        List<Position> portfolio = request.positions().stream().map(PricingController::toPosition).toList();
        return HistoricalVar.compute(portfolio, request.logReturns(), request.confidence());
    }

    private static Position toPosition(PositionRequest p) {
        return switch (p.kind()) {
            case STOCK -> new StockPosition(p.symbol(), p.quantity(), p.spot());
            case OPTION -> {
                if (p.strike() == null || p.volatility() == null || p.expiry() == null || p.type() == null) {
                    throw new IllegalArgumentException(
                            "option position %s requires strike, volatility, expiry and type".formatted(p.symbol()));
                }
                yield new OptionPosition(p.symbol(), p.quantity(), new Option(
                        p.spot(), p.strike(),
                        p.rate() == null ? 0.0 : p.rate(),
                        p.dividendYield() == null ? 0.0 : p.dividendYield(),
                        p.volatility(), p.expiry(), p.type()));
            }
        };
    }
}
