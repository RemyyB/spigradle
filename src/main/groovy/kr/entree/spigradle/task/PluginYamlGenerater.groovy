package kr.entree.spigradle.task

import kr.entree.spigradle.extension.PluginAttributes
import kr.entree.spigradle.util.Mapper
import kr.entree.spigradle.util.inspector.ByteInspector
import kr.entree.spigradle.util.inspector.InspectorResult
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * Created by JunHyung Lim on 2019-12-12
 */
class PluginYamlGenerater extends DefaultTask {
    @Input
    PluginAttributes attr
    @Input
    String encoding = 'UTF-8'

    @TaskAction
    def createPluginYaml() {
        def file = new File(temporaryDir, 'plugin.yml')
        writePluginYaml(file.newWriter(encoding))
        project.tasks.findAll {
            it instanceof Jar
        } each {
            it.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            it.from file
        }
    }

    def writePluginYaml(Writer writer) {
        def inspected = new ByteInspector(project).doInspect()
        createYaml().dump(createMap(inspected), writer)
    }

    def createMap(InspectorResult inspected) {
        def attributes = [
                'main'   : attr.main.getOrElse(inspected.getMainOrThrow()),
                'name'   : attr.name.getOrElse(project.name),
                'version': attr.version.getOrElse(project.version)
        ]
        PluginAttributes.declaredFields.grep {
            !it.synthetic
        }.each {
            it.setAccessible(true)
            def property = it.get(attr)
            def value = null
            if (property instanceof NamedDomainObjectContainer) {
                def map = property.getAsMap()
                if (!map.isEmpty()) {
                    value = map
                }
            } else if (property instanceof Provider) {
                value = property.getOrNull()
            }
            if (checkValid(value)) {
                def key = it.name.toLowerCase()
                attributes[key] = value
            }
        }
        return Mapper.map(attributes)
    }

    static boolean checkValid(Object obj) {
        if (obj == null) {
            return false
        }
        if ((obj instanceof Map || obj instanceof Collection)
                && obj.isEmpty()) {
            return false
        }
        return true
    }

    static Yaml createYaml() {
        def options = new DumperOptions()
        options.with {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            prettyFlow = true
            indicatorIndent = indent - 1
        }
        return new Yaml(options)
    }
}