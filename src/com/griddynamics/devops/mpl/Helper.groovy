//
// Copyright (c) 2018 Grid Dynamics International, Inc. All Rights Reserved
// https://www.griddynamics.com
//
// Classification level: Public
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// $Id: $
// @Project:     MPL
// @Description: Shared Jenkins Modular Pipeline Library
//

package com.griddynamics.devops.mpl

import java.nio.file.Paths

import com.cloudbees.groovy.cps.NonCPS

import org.jenkinsci.plugins.workflow.cps.CpsGroovyShellFactory
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.libs.LibrariesAction

import hudson.model.Run
import hudson.FilePath

/**
 * Manages all helpers to interact with low-level groovy
 *
 * @author Sergei Parshev <sparshev@griddynamics.com>
 */
abstract class Helper {
  /**
   * Get a new shell with set some specific variables
   *
   * @param vars  Predefined variables to include in the shell
   * @return  cps groovy shell object
   */
  @NonCPS
  static Object getShell(Map vars = [:]) {
    def ex = CpsThread.current().getExecution()
    def shell = new CpsGroovyShellFactory(ex).withParent(ex.getShell()).build()
    vars.each { key, val -> shell.setVariable(key, val) }
    shell
  }

  /**
   * Getting a list of modules for each loaded library with modules data
   * Idea from LibraryAdder.findResource() function
   *
   * @param path  Module resource path
   * @return  list of maps with pairs "module path: module source code"
   *
   * @see org.jenkinsci.plugins.workflow.libs.LibraryAdder#findResources(CpsFlowExecution execution, String name)
   */
  static List getModulesList(String path) {
    def executable = CpsThread.current()?.getExecution()?.getOwner()?.getExecutable()
    if( ! (executable instanceof Run) )
      throw new MPLException('Current executable is not a jenkins Run')

    def action = executable.getAction(LibrariesAction.class)
    if( action == null )
      throw new MPLException('Unable to find LibrariesAction in the current Run')

    def modules = []
    def libs = new FilePath(executable.getRootDir()).child('libs')
    action.getLibraries().each { lib ->
      MPLManager.instance.getModulesLoadPaths().each { modulesPath ->
        def libPath = Paths.get(lib.name, 'resources', modulesPath, path).toString()
        def f = libs.child(libPath)
        if( f.exists() ) modules += [[libPath, f.readToString()]]
      }
    }
    return modules
  }

  /**
   * Helps with merging two maps recursively
   *
   * @param base     map for modification
   * @param overlay  map to override values in the _base_ map
   * @return  modified base map
   */
  static Map mergeMaps(Map base, Map overlay) {
    if( ! (base in Map) ) base = [:]
    if( ! (overlay in Map) ) return overlay
    overlay.each { key, val ->
      base[key] = base[key] in Map ? mergeMaps(base[key], val) : val
    }
    base
  }

  /**
   * Deep copy of the Map or List
   *
   * @param value      value to deep copy
   *
   * @return  value type without any relation to the original value
   */
  @NonCPS
  static cloneValue(value) {
    def out

    if( value in Map )
      out = value.collectEntries { k, v -> [k, cloneValue(v)] }
    else if( value in List )
      out = value.collect { cloneValue(it) }
    else
      out = value

    return out
  }

  /**
   * Helps to run source code in the new shell with predefined vars
   * Also it's overriden by tests to handle the module execution
   *
   * @param src   source code of the module
   * @param path  resource path of the module to track it
   * @param vars  predefined variables for the run
   */
  static void runModule(String src, String path, Map vars = [:]) {
    getShell(vars).evaluate(src, path)
  }

  /**
   * Cutting a stacktrace to just first execution of the module and one before
   *
   * @param exception  container of the stacktrace
   * @return  List with stack trace elements
   */
  static StackTraceElement[] getModuleStack(Throwable exception) {
    List stack = exception.getStackTrace()
    for( def i = stack.size(); i--; i > 0 ) {
      if( stack[i-1].getFileName()?.contains('vars/MPLModule.groovy') )
        break
      else
        stack.remove(i)
    }
    stack as StackTraceElement[]
  }
  
  /**
   * Special function to return exception if someone tries to use MPLConfig in a wrong way
   * Basically used just to be overridden on the unit tests side.
   *
   * @param config  current MPLConfig configuration
   *
   * @return  Set of entries - but only when overridden by unit tests
   */
  static Set configEntrySet(Map config) {
    throw new MPLException('Forbidden to iterate over MPLConfig')
  }
}
