/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.backends.lwjgl3.audio.mock.MockAudio;
import com.badlogic.gdx.utils.Clipboard;
import org.jetbrains.annotations.NotNull;

public class MockedApp implements Application {

  final ApplicationListener getApplicationListener;
  final CustomGraphics graphics;
  final Lwjgl3Files files = new Lwjgl3Files();

  final Audio audio = new MockAudio();

  public MockedApp(@NotNull final ApplicationListener listener, final long windowHandle) {
    this.getApplicationListener = listener;
    this.graphics = new CustomGraphics(new CustomLwjgl3GL20(), new CustomLwjgl3GL30(),
        windowHandle);
  }

  @Override
  public ApplicationListener getApplicationListener() {
    return getApplicationListener;
  }

  @Override
  public Graphics getGraphics() {
    return graphics;
  }

  @Override
  public Audio getAudio() {
    return audio;
  }

  @Override
  public Input getInput() {
    return null;
  }

  @Override
  public Files getFiles() {
    return files;
  }

  @Override
  public Net getNet() {
    return null;
  }

  @Override
  public void log(final String tag, final String message) {

  }

  @Override
  public void log(final String tag, final String message, final Throwable exception) {

  }

  @Override
  public void error(final String tag, final String message) {

  }

  @Override
  public void error(final String tag, final String message, final Throwable exception) {

  }

  @Override
  public void debug(final String tag, final String message) {

  }

  @Override
  public void debug(final String tag, final String message, final Throwable exception) {

  }

  @Override
  public void setLogLevel(final int logLevel) {

  }

  @Override
  public int getLogLevel() {
    return 0;
  }

  @Override
  public void setApplicationLogger(ApplicationLogger applicationLogger) {
  }

  @Override
  public ApplicationLogger getApplicationLogger() {
    return null;
  }

  @Override
  public ApplicationType getType() {
    return ApplicationType.Desktop;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public long getJavaHeap() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }

  @Override
  public long getNativeHeap() {
    return getJavaHeap();
  }

  @Override
  public Preferences getPreferences(final String name) {
    return null;
  }

  @Override
  public Clipboard getClipboard() {
    return null;
  }

  @Override
  public void postRunnable(final Runnable runnable) {

  }

  @Override
  public void exit() {

  }

  @Override
  public void addLifecycleListener(final LifecycleListener listener) {

  }

  @Override
  public void removeLifecycleListener(final LifecycleListener listener) {

  }
}
