package me.serce.solidity.ide.run

import com.google.common.base.Strings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import me.serce.solidity.lang.core.SolElementType
import me.serce.solidity.lang.psi.SolFunctionDefinition
import me.serce.solidity.lang.psi.elementType

class SolidityRunConfigProducer : RunConfigurationProducer<SolidityRunConfigBase>(SolidityConfigurationType.getInstance()) {
  override fun isConfigurationFromContext(configuration: SolidityRunConfigBase?, context: ConfigurationContext?): Boolean {
    return ifSolidityRunConfig(configuration) { config ->
      val funcName = config.getPersistentData().functionName ?: return@ifSolidityRunConfig false
      val contrName = config.getPersistentData().contractName ?: return@ifSolidityRunConfig false
      val psiElement = context?.location?.psiElement ?: return@ifSolidityRunConfig false
      val func = searchFunction(psiElement) ?: return@ifSolidityRunConfig false
      return@ifSolidityRunConfig funcName == func.name && contrName == func.contract.name
    }
  }

  private fun ifSolidityRunConfig(config: SolidityRunConfigBase?, action: (config: SolidityRunConfig) -> Boolean): Boolean {
    if (config != null && config::class.qualifiedName == "me.serce.solidity.ide.run.SolidityRunConfig") {
      if (config is SolidityRunConfig) {
        return action(config)
      }
    }
    return false
  }

  override fun setupConfigurationFromContext(configuration: SolidityRunConfigBase?, context: ConfigurationContext?, sourceElement: Ref<PsiElement>?): Boolean {
    if (context == null || configuration == null || sourceElement == null) {
      return false
    }
    val solFunctionDefinition = searchFunction(sourceElement.get()) ?: return false
    if (!SearchUtils.runnableFilter.invoke(solFunctionDefinition)) {
      return false
    }
    if (Strings.isNullOrEmpty(solFunctionDefinition.name)) {
      return false
    }
    return ifSolidityRunConfig(configuration) { configuration ->
      configuration.setModule(context.module)
      configuration.getPersistentData().setFunction(solFunctionDefinition)
      configuration.name = solFunctionDefinition.contract.name + "." + solFunctionDefinition.name
      true
    }
  }
}

private fun searchFunction(sourceElement: PsiElement?): SolFunctionDefinition? {
  var get = sourceElement
  while (get != null && get !is SolFunctionDefinition) {
    get = get.parent
  }
  return if (get == null || get !is SolFunctionDefinition) null else get
}

class SolidityRunLineMarkerProvider : RunLineMarkerContributor() {
  override fun getInfo(e: PsiElement): RunLineMarkerContributor.Info? {
    if (!hasJavaSupport) return null

    val elementType = e.elementType
    if (elementType is SolElementType && elementType.name == "Identifier") {
      val searchFunction = e.parent ?: return null
      if (searchFunction is SolFunctionDefinition && SearchUtils.runnableFilter.invoke(searchFunction)) {
        val actions = ExecutorAction.getActions(0).filter { it.toString().startsWith("Run context configuration") }.toTypedArray()
        return RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run,
          { element1 -> StringUtil.join(ContainerUtil.mapNotNull<AnAction, String>(actions) { action -> RunLineMarkerContributor.getText(action, element1) }, "\n") },
          actions)
      }
    }
    return null
  }
}
