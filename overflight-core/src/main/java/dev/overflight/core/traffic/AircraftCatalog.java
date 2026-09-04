package dev.overflight.core.traffic;

import dev.overflight.core.atmo.SchmidtAppleman.EngineProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The default mix of what flies overhead. Weights are relative and get
 * normalised, so a config that raises military traffic does not have to lower
 * everything else to compensate.
 */
public final class AircraftCatalog {
    private final List<AircraftType> types;
    private final double totalWeight;

    public AircraftCatalog(List<AircraftType> types) {
        this.types = Collections.unmodifiableList(new ArrayList<AircraftType>(types));
        double sum = 0.0;
        for (int i = 0; i < this.types.size(); i++) {
            sum += this.types.get(i).weight;
        }
        this.totalWeight = sum;
    }

    public List<AircraftType> types() {
        return types;
    }

    public AircraftType byId(String id) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).id.equals(id)) {
                return types.get(i);
            }
        }
        return null;
    }

    /** Picks a type by weight. */
    public AircraftType pick(Rng rng) {
        double roll = rng.nextDouble() * totalWeight;
        for (int i = 0; i < types.size(); i++) {
            roll -= types.get(i).weight;
            if (roll <= 0.0) {
                return types.get(i);
            }
        }
        return types.get(types.size() - 1);
    }

    public static AircraftCatalog defaults() {
        EngineProfile turbofan = EngineProfile.modernTurbofan();
        EngineProfile turbojet = EngineProfile.lowBypassTurbojet();
        EngineProfile prop = EngineProfile.turboprop();

        List<AircraftType> list = new ArrayList<AircraftType>();
        list.add(new AircraftType("airliner_narrowbody", 44.0, 310, 390, 2, turbofan, 35.8, 0.78));
        list.add(new AircraftType("airliner_widebody", 18.0, 330, 410, 2, turbofan, 64.8, 0.85));
        list.add(new AircraftType("airliner_quadjet", 2.0, 310, 390, 4, turbofan, 68.4, 0.85));
        list.add(new AircraftType("regional_jet", 12.0, 280, 360, 2, turbofan, 26.0, 0.74));
        list.add(new AircraftType("turboprop", 8.0, 180, 250, 2, prop, 27.0, 0.45));
        list.add(new AircraftType("cargo", 6.0, 290, 350, 4, turbofan, 64.4, 0.84));
        list.add(new AircraftType("bizjet", 5.0, 410, 510, 2, turbofan, 28.5, 0.85));
        list.add(new AircraftType("military_transport", 3.0, 240, 330, 4, turbojet, 51.8, 0.74));
        list.add(new AircraftType("military_fighter", 1.5, 250, 450, 2, turbojet, 13.6, 0.92, 1, 4));
        list.add(new AircraftType("military_tanker", 0.4, 260, 330, 4, turbojet, 47.6, 0.80));
        list.add(new AircraftType("high_altitude_recon", 0.1, 550, 700, 1, turbojet, 31.4, 0.70));
        return new AircraftCatalog(list);
    }
}
