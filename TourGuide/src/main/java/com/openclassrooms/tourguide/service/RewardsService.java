package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

/**
 * Ce service calcule les récompenses pour un utilisateur.
 *
 * Il regarde où l'utilisateur est allé,
 * puis vérifie s'il était près d'une attraction.
 * Si oui, il donne des points de récompense.
 *
 * On utilise des "futures" pour aller plus vite quand il y a beaucoup d'utilisateurs.
 * Et un "sémaphore" pour ne pas faire trop d'appels en même temps.
 */
@Service
public class RewardsService {

	/** 1 mille nautique = 1.15 miles terrestres */
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	/** Distance par défaut pour dire "près" : 10 miles */
	private int defaultProximityBuffer = 10;

	/** Distance actuelle pour dire "près" */
	private int proximityBuffer = defaultProximityBuffer;

	/** Distance maximale pour considérer une attraction : 200 miles */
	private int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	/** On limite à 10 appels en même temps pour ne pas surcharger RewardCentral */
	private final Semaphore rewardSemaphore = new Semaphore(10);

	/**
	 * Constructeur : on donne les outils nécessaires
	 * @param gpsUtil pour avoir la liste des attractions
	 * @param rewardCentral pour calculer les points
	 */
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	/**
	 * Change la distance pour dire "près"
	 * @param proximityBuffer nouvelle distance en miles
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Remet la distance par défaut (10 miles)
	 */
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Demande les points de récompense en parallèle (plus rapide)
	 * On limite à 10 appels en même temps avec un "sémaphore"
	 * @param attraction l'attraction
	 * @param user l'utilisateur
	 * @return une "promesse" qui contiendra les points plus tard
	 */
	public CompletableFuture<Integer> getRewardPointsAsync(Attraction attraction, User user) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				rewardSemaphore.acquire(); // On prend un "ticket"
				return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return 0;
			} finally {
				rewardSemaphore.release(); // On libère le ticket
			}
		});
	}

	/**
	 * Méthode SYNCHRONE pour le contrôleur (il ne peut pas attendre un future)
	 * @param attraction l'attraction
	 * @param user l'utilisateur
	 * @return les points de récompense
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calcule TOUTES les récompenses pour un utilisateur
	 *
	 * 1. On prend tous les lieux visités
	 * 2. On regarde chaque attraction
	 * 3. Si l'utilisateur était près ET pas déjà récompensé → on calcule les points
	 * 4. On lance TOUS les calculs en même temps (mais max 10 en même temps)
	 * 5. On attend que tout soit fini
	 */
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (VisitedLocation visitedLocation : userLocations) {
			for (Attraction attraction : attractions) {
				// Déjà récompensé ? → on passe
				boolean alreadyRewarded = user.getUserRewards().stream()
						.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

				if (!alreadyRewarded && nearAttraction(visitedLocation, attraction)) {
					// On lance le calcul des points en parallèle (max 10 en même temps)
					CompletableFuture<Void> future = getRewardPointsAsync(attraction, user)
							.thenAccept(points -> {
								user.addUserReward(new UserReward(visitedLocation, attraction, points));
							});
					futures.add(future);
				}
			}
		}

		// On attend que TOUTES les récompenses soient calculées
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	/**
	 * Vérifie si un lieu est dans la zone d'une attraction (200 miles max)
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= attractionProximityRange;
	}

	/**
	 * Vérifie si l'utilisateur était "près" d'une attraction
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	/**
	 * Calcule la distance entre deux points sur Terre (en miles)
	 */
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2) +
				Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}
}