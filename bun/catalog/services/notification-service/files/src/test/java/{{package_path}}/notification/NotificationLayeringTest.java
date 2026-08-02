package {{package}}.notification;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
@AnalyzeClasses(
        packages = "{{package}}.notification",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class NotificationLayeringTest {
    private static final DescribedPredicate<JavaClass> NOT_A_CONCRETE_IMPL =
            new DescribedPredicate<>("not a concrete Adapter/Impl/Repository/ServiceImpl") {
                @Override
                public boolean test(JavaClass javaClass) {
                    String name = javaClass.getSimpleName();
                    return !name.matches(".*(Adapter|Impl|Repository|ServiceImpl)$");
                }
            };
    @ArchTest
    static final ArchRule controllersShouldNotDependOnConcreteAdapters = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().onlyAccessClassesThat(NOT_A_CONCRETE_IMPL)
            .allowEmptyShould(true);
}
