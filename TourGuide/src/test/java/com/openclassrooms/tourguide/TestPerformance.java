package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

/**
 * Classe de tests de performance pour TourGuide.
 *
 * On mesure le temps pour :
 * - Suivre la position de 100 000 utilisateurs
 * - Calculer les récompenses pour 100 000 utilisateurs
 *
 * On utilise CompletableFuture et ExecutorService pour paralléliser.
 */
public class TestPerformance {

	/*
	 * A note on performance improvements:
	 *
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 *
	 * InternalTestHelper.setInternalUserNumber(100000);
	 *
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 *
	 * These are performance metrics that we are trying to hit:
	 *
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	/**
	 * Teste le suivi de position pour 100 000 utilisateurs.
	 * Doit finir en moins de 15 minutes.
	 *
	 * On parallélise l'appel à trackUserLocation pour chaque utilisateur
	 * avec un ExecutorService (100 threads).
	 */
	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		// Important : on coupe le Tracker pour ne pas doubler le travail
		tourGuideService.tracker.stopTracking();

		List<User> allUsers = tourGuideService.getAllUsers();
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		ExecutorService executor = Executors.newFixedThreadPool(100);
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(
						() -> tourGuideService.trackUserLocation(user), executor))
				.collect(Collectors.toList());

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();

		stopWatch.stop();

		System.out.println("highVolumeTrackLocation: Time Elapsed: " +
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}


	/**
	 * Teste le calcul des récompenses pour 100 000 utilisateurs
	 * Doit finir en moins de 20 minutes
	 *
	 * On parallélise avec ExecutorService (100 threads)
	 */
	@Test
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(100000);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		tourGuideService.tracker.stopTracking();

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(u -> u.addToVisitedLocations(
				new VisitedLocation(u.getUserId(), attraction, new Date())));

		// PARALLÉLISATION ICI
		ExecutorService executor = Executors.newFixedThreadPool(100);
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(user -> CompletableFuture.runAsync(
						() -> rewardsService.calculateRewards(user), executor))
				.collect(Collectors.toList());

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();

		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		stopWatch.stop();


		System.out.println("highVolumeGetRewards: Time Elapsed: " +
				TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
}