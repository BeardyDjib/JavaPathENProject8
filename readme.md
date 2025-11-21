TourGuide — Projet d’Optimisation, Concurrence & CI/CD

(Projet OpenClassrooms - Java Path EN - P8)

🧭 Objectif du projet

L’application TourGuide permet :

d’obtenir les attractions touristiques proches,

de recevoir des récompenses via RewardCentral,

d’obtenir des offres de voyage personnalisées.

Le projet avait plusieurs défis majeurs :

Corriger les tests qui échouaient

Implémenter correctement les 5 attractions les plus proches

Résoudre des erreurs de concurrence (ConcurrentModificationException)

Optimiser les performances massives (100 000 utilisateurs)

Mettre en place un pipeline CI/CD GitHub Actions

Finaliser les livrables & documentation

✅ Étape 2 : Résumé clair de tout ce qui a été corrigé
✔️ Étape 2 — Correction des tests & problèmes de concurrence

Problèmes rencontrés :

ConcurrentModificationException lors du calcul des récompenses

Tests qui échouaient car RewardService utilisait mal les listes

Mauvaise gestion de l’historique utilisateur

Solutions apportées :

Remplacement de certaines listes par CopyOnWriteArrayList

Correction de l’algorithme calculateRewards

Stabilisation du comportement du service

Aucun test modifié, seulement la logique métier

✔️ Étape 3 — Implémentation des 5 attractions les plus proches

Suppression du @Ignore dans TestTourGuideService

Ajout d’une vraie logique : tri par distance

Ajout d’un JSON structuré dans le controller

Résultat conforme au TODO du projet

✔️ Étape 4 — Performance (objectif < 20 minutes)

Problème initial :

Le service RewardService recalculait toutes les attractions pour toutes les positions

Résultat : 40+ minutes pour 100 000 utilisateurs

Optimisations :

Utilisation de CompletableFuture

Limitation des appels RewardCentral avec Semaphore

Parallélisation contrôlée avec un ThreadPool 100 threads

calculateRewards ne regarde plus que la dernière position

Les tests passent maintenant :

trackLocation ≈ 200s

getRewards ≈ 500s

✔️ Étape 5 — Pipeline CI GitHub Actions

Mise en place d’un workflow CI :

Build Maven

Tests unitaires (TestPerformance désactivé)

Packaging JAR

Upload de l’artefact

Problèmes rencontrés :

Maven exécuté dans le mauvais répertoire

Dépendances tierces (gpsUtil, TripPricer, RewardCentral) non présentes

Correction via dossier libs/ + installation manuelle

Résultat : pipeline opérationnel et vert ✔️

✔️ Étape 6 — Livrables & Documentation

Livrables fournis :

Code propre et commenté

README complet

historique.txt pour tracking

CI/CD fonctionnel

Tests automatisés stables

Logique métier respectée

🚀 Comment exécuter le projet
Prérequis

Java 17

Maven 3.8+

Git

Compilation