package io.github.marmer.annotationprocessing

import java.time.LocalDateTime
import javax.annotation.processing.Generated
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic

class MatcherGenerationProcessorWorker(
    private val timeProvider: () -> LocalDateTime,
    private val processingEnv: ProcessingEnvironment,
    private val generatorName: String
) {
    fun getSupportedSourceVersion() = SourceVersion.latestSupported()

    fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) {
            return false
        }
        processingEnv.logNote("Annotation processor for hamcrest matcher generation started")
        return if (annotations.contains<MatcherConfiguration>()) {
            roundEnv.getElementsAnnotatedWith<MatcherConfiguration>()
                .forEach { generateMatcherBy(it) }
            true
        } else {
            false
        }
    }

    private fun generateMatcherBy(generationConfiguration: Element) {
        getAllTypeElementsFor(generationConfiguration)
            .forEach {
                if (it.isSelfGenerated()) {
                    printSkipNoteBecauseOfSelfGenerationFor(it)
                } else {
                    MatcherGenerator(
                        processingEnv,
                        it,
                        timeProvider,
                        generatorName,
                        listOf(generationConfiguration),
                        generationConfiguration.annotationMirrors.single { it.isTypeOf<MatcherConfiguration>() }
                    ).generate()
                }
            }
    }

    private fun getAllTypeElementsFor(configurationType: Element): List<TypeElement> {
        return configurationType.getMatcherConfiguration()
            .value
            .distinct()
            .flatMap { getAllTypeElementsFor(it, configurationType) }
            .distinct()
    }

    private fun Element.getMatcherConfiguration() = getAnnotation(MatcherConfiguration::class.java)

    private fun getAllTypeElementsFor(
        currentQualifiedTypeOrPackageName: String,
        configurationType: Element
    ): List<TypeElement> {
        val typeElementsForName = processingEnv.elementUtils
            .getAllPackageElements(currentQualifiedTypeOrPackageName)
            .flatMap { it.enclosedElements }
            .map { it as TypeElement }
            .plus(
                getHighestNestingType(
                    processingEnv
                        .elementUtils
                        .getTypeElement(currentQualifiedTypeOrPackageName)
                )
            ).filterNotNull()

        if (typeElementsForName.isEmpty())
            printSkipWarningBecauseOfNotExistingTypeConfigured(configurationType, currentQualifiedTypeOrPackageName)

        return typeElementsForName
    }

    private fun getHighestNestingType(typeElement: TypeElement?): TypeElement? =
        if (typeElement != null && typeElement.enclosingElement is TypeElement)
            getHighestNestingType(typeElement.enclosingElement as TypeElement)
        else
            typeElement

    private fun printSkipWarningBecauseOfNotExistingTypeConfigured(
        configurationClass: Element,
        qualifiedTypeOrPackageName: String
    ) {
        configurationClass.annotationMirrors
            .filter { it.isTypeOf<MatcherConfiguration>() }
            .forEach {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.MANDATORY_WARNING,
                    "Neither a type nor a type exists for '$qualifiedTypeOrPackageName'",
                    configurationClass,
                    it,
                    it.getAnnotationValueForValue()
                )
            }
    }

    private fun printSkipNoteBecauseOfSelfGenerationFor(typeElement: TypeElement) {
        typeElement.annotationMirrors
            .filter { it.isTypeOf<Generated>() }
            .forEach {
                processingEnv.logNote(
                    "Generation skipped for: '${typeElement.qualifiedName}' because is is already generated by this processor", // TODO: marmer 18.06.2021 Test for this message with lines etc.
                    typeElement,
                    it,
                    it.getAnnotationValueForField("value")
                )
            }
    }

    private fun Element.isSelfGenerated(): Boolean =
        getAnnotation(Generated::class.java)
            .let {
                it != null && it.value.any { value -> value == generatorName }
            }

    private inline fun <reified T : Annotation> AnnotationMirror.isTypeOf() =
        annotationType.asElement().toString() == T::class.qualifiedName

    private inline fun <reified T : Annotation> RoundEnvironment.getElementsAnnotatedWith() =
        getElementsAnnotatedWith(T::class.java)

    private inline fun <reified T> Set<TypeElement>.contains() =
        this.find { T::class.qualifiedName == it.qualifiedName.toString() } != null
}

internal fun AnnotationMirror.getAnnotationValueForField(fieldName: String) =
    elementValues.get(elementValues.keys.first { it.simpleName.contentEquals(fieldName) })

internal fun AnnotationMirror.getAnnotationValueForValue() =
    getAnnotationValueForField("value")

internal fun ProcessingEnvironment.logNote(
    message: String,
    element: Element? = null,
    annotationMirror: AnnotationMirror? = null,
    annotationValue: AnnotationValue? = null
) = messager.printMessage(Diagnostic.Kind.NOTE, message, element, annotationMirror, annotationValue)

internal val Element.isPrivate: Boolean
    get() = modifiers.contains(Modifier.PRIVATE)

internal val Element.isPublic: Boolean
    get() = modifiers.contains(Modifier.PUBLIC)

internal val Element.isStatic: Boolean
    get() = modifiers.contains(Modifier.STATIC)
