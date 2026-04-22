---
trigger: always_on
---

If a bug or discrepancy is reported or found, write a test that would cover those bases. There are two types of tests, ones that don't require running game logic (the unit tests that run on build), and the ones that do (runGameTestServer); write the test for the most logical choice, and run the test to make sure it passes, either matching the logic from 1.12.2, or the request at hand.

Write tests for expected behavior as well, especially for ports, refactors, or new features.