package com.openclassrooms.tourguide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

/**
 * Contrôleur principal de l'application TourGuide.
 *
 * Il gère toutes les requêtes HTTP :
 * - Page d'accueil
 * - Localisation de l'utilisateur
 * - 5 attractions les plus proches (avec distance et points)
 * - Récompenses
 * - Offres de voyage
 */
@RestController
public class TourGuideController {

    @Autowired
    TourGuideService tourGuideService;

    /**
     * Page d'accueil
     * @return un message de bienvenue
     */
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    /**
     * Retourne la position actuelle de l'utilisateur
     * @param userName nom de l'utilisateur
     * @return la dernière position connue
     */
    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }

    /**
     * Retourne les 5 attractions les plus proches de l'utilisateur
     *
     * Ce que tu vois dans Postman :
     * - Nom de l'attraction
     * - Ses coordonnées (lat/long)
     * - Tes coordonnées (lat/long)
     * - Distance en miles
     * - Points de récompense
     *
     * On limite à 5 résultats, même si l'attraction est très loin.
     */
    @RequestMapping("/getNearbyAttractions")
    public List<Map<String, Object>> getNearbyAttractions(@RequestParam String userName) {
        VisitedLocation userLocation = tourGuideService.getUserLocation(getUser(userName));
        List<Attraction> allAttractions = tourGuideService.getNearByAttractions(userLocation);

        return allAttractions.stream()
                .limit(5) // On prend seulement les 5 premières
                .map(attraction -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", attraction.attractionName);
                    info.put("attractionLocation", Map.of(
                            "latitude", attraction.latitude,
                            "longitude", attraction.longitude
                    ));
                    info.put("userLocation", Map.of(
                            "latitude", userLocation.location.latitude,
                            "longitude", userLocation.location.longitude
                    ));
                    info.put("distanceMiles", tourGuideService.getDistanceInMiles(userLocation.location, attraction));
                    info.put("rewardPoints", tourGuideService.getRewardPoints(attraction, getUser(userName)));
                    return info;
                })
                .collect(Collectors.toList());
    }

    /**
     * Retourne toutes les récompenses de l'utilisateur
     * @param userName nom de l'utilisateur
     * @return liste des récompenses
     */
    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }

    /**
     * Retourne les offres de voyage pour l'utilisateur
     * @param userName nom de l'utilisateur
     * @return liste des offres
     */
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
    }

    /**
     * Récupère l'utilisateur à partir de son nom
     * @param userName nom de l'utilisateur
     * @return l'objet User
     */
    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }
}