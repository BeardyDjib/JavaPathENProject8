package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
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
 * Il regarde où l'utilisateur est allé récemment,
 * puis vérifie s'il était près d'une attraction.
 * Si oui, il demande à RewardCentral combien de points donner.
 *
 * Pour aller plus vite quand il y a beaucoup d'utilisateurs :
 * - on utilise des CompletableFuture pour lancer plusieurs calculs en parallèle ;
 * - on utilise un "sémaphore" pour limiter le nombre d'appels simultanés à RewardCentral ;
 * - on utilise un "pool de threads" pour exécuter ces tâches en parallèle.
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

	/** On limite à 100 appels en même temps pour ne pas surcharger RewardCentral */
	private final Semaphore rewardSemaphore = new Semaphore(100);

	/** On crée 100 "threads" pour lancer plusieurs calculs en parallèle */
	private final ExecutorService executor = Executors.newFixedThreadPool(100);

	/**
	 * Constructeur : on donne les outils nécessaires.
	 *
	 * @param gpsUtil        pour avoir la liste des attractions
	 * @param rewardCentral  pour calculer les points de récompense
	 */
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	/**
	 * Change la distance pour dire "près".
	 *
	 * @param proximityBuffer nouvelle distance en miles
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Remet la distance par défaut (10 miles).
	 */
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Demande les points de récompense en parallèle (plus rapide).
	 *
	 * On limite à 100 appels en même temps avec un "sémaphore"
	 * pour ne pas surcharger RewardCentral.
	 * On utilise le pool de threads pour exécuter les appels.
	 *
	 * @param attraction l'attraction
	 * @param user       l'utilisateur
	 * @return une "promesse" (CompletableFuture) qui contiendra les points plus tard
	 */
	public CompletableFuture<Integer> getRewardPointsAsync(Attraction attraction, User user) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				// On prend un "ticket" avant d'appeler RewardCentral
				rewardSemaphore.acquire();
				return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return 0;
			} finally {
				// On libère le "ticket" pour laisser la place aux autres
				rewardSemaphore.release();
			}
		}, executor);
	}

	/**
	 * Méthode SYNCHRONE pour le contrôleur (il ne peut pas attendre un future).
	 *
	 * @param attraction l'attraction
	 * @param user       l'utilisateur
	 * @return les points de récompense
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calcule les récompenses pour un utilisateur.
	 *
	 * Optimisations :
	 * 1. On ne regarde que la DERNIÈRE position visitée (pas tout l'historique).
	 * 2. On prépare un Set des attractions déjà récompensées pour aller plus vite.
	 * 3. Pour chaque attraction proche et pas encore récompensée :
	 *    - on lance le calcul des points en parallèle (max 100 en même temps grâce au sémaphore).
	 * 4. On attend que tous les calculs soient terminés.
	 *
	 * @param user l'utilisateur pour lequel calculer les récompenses
	 */
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();

		// Si l'utilisateur n'a aucune position, on ne peut rien calculer
		if (userLocations.isEmpty()) {
			return;
		}

		// 1) On prend uniquement la dernière position visitée
		VisitedLocation lastVisitedLocation = user.getLastVisitedLocation();

		// 2) On récupère la liste des attractions
		List<Attraction> attractions = gpsUtil.getAttractions();

		// 3) On prépare un Set des attractions déjà récompensées pour cet utilisateur
		Set<String> rewardedAttractions = user.getUserRewards().stream()
				.map(r -> r.attraction.attractionName)
				.collect(Collectors.toSet());

		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Attraction attraction : attractions) {

			// Déjà récompensé pour cette attraction ? → on passe
			if (rewardedAttractions.contains(attraction.attractionName)) {
				continue;
			}

			// Pas assez proche ? → on passe
			if (!nearAttraction(lastVisitedLocation, attraction)) {
				continue;
			}

			// On lance le calcul des points en parallèle (max 100 en même temps via le sémaphore)
			CompletableFuture<Void> future = getRewardPointsAsync(attraction, user)
					.thenAccept(points -> {
						// Quand les points sont calculés, on ajoute la récompense à l'utilisateur
						user.addUserReward(new UserReward(lastVisitedLocation, attraction, points));
					});

			futures.add(future);
		}

		// 4) On attend que TOUTES les récompenses soient calculées
		if (!futures.isEmpty()) {
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		}
	}

	/**
	 * Vérifie si un lieu est dans la zone d'une attraction (200 miles max).
	 *
	 * @param attraction l'attraction
	 * @param location   la position à tester
	 * @return true si la position est dans le rayon de l'attraction
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= attractionProximityRange;
	}

	/**
	 * Vérifie si l'utilisateur était "près" d'une attraction.
	 *
	 * @param visitedLocation la position visitée par l'utilisateur
	 * @param attraction      l'attraction
	 * @return true si la distance est inférieure au "proximityBuffer"
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	/**
	 * Calcule la distance entre deux points sur Terre (en miles).
	 *
	 * @param loc1 premier point (latitude, longitude)
	 * @param loc2 deuxième point (latitude, longitude)
	 * @return la distance en miles
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

	/**
	 * Ferme le pool de threads quand l'application s'arrête.
	 */
	@PreDestroy
	public void shutdown() {
		executor.shutdown();
	}
}
