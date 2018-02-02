package annotationprocesser;

import com.f1l.zygote.annotation.BindView;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/**
 * Created by huying.sxd on 2018/1/11.
 */
@AutoService(Processor.class)
public class BindViewProcesser extends AbstractProcessor {

    private Filer mFiler;
    private Messager mMessager;
    private Elements mElementUtils;
    //classname , element list
    private HashMap<String,ArrayList<Element>> mElementMap;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mFiler = processingEnvironment.getFiler();
        mMessager = processingEnvironment.getMessager();
        mElementUtils = processingEnvironment.getElementUtils();
        mElementMap = new HashMap<>();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(BindView.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> bindViewElements = roundEnvironment.getElementsAnnotatedWith(BindView.class);
        for (Element element : bindViewElements) {

            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            //类名
            String enclosingName = enclosingElement.getQualifiedName().toString();
            note(String.format("enclosingName = %s", enclosingElement));

            VariableElement bindViewElement = (VariableElement) element;
            String bindViewFiledName = bindViewElement.getSimpleName().toString();
            String bindViewFiledClassType = bindViewElement.asType().toString();

            if (mElementMap.get(enclosingName) == null) {
                mElementMap.put(enclosingName,new ArrayList<Element>());
            }
            mElementMap.get(enclosingName).add(element);
            //因为BindView只作用于filed，所以这里可直接进行强转
            /*VariableElement bindViewElement = (VariableElement) element;
            String bindViewFiledName = bindViewElement.getSimpleName().toString();
            String bindViewFiledClassType = bindViewElement.asType().toString();

            BindView bindView = element.getAnnotation(BindView.class);
            int id = bindView.value();
            note(String.format("%s %s = %d", bindViewFiledClassType, bindViewFiledName, id));*/



            return true;
        }

        for (String enclosingName: mElementMap.keySet()) {
            ArrayList<Element> elements = mElementMap.get(enclosingName);
            PackageElement packageElement = mElementUtils.getPackageOf(elements.get(0));
            String pkName = packageElement.getQualifiedName().toString();
            note(String.format("package = %s", pkName));

            TypeMirror typeMirror = elements.get(0).getEnclosingElement().asType();

            MethodSpec.Builder methodBuilder = null;
            methodBuilder = MethodSpec.methodBuilder("bind")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(void.class)
                    .addParameter(TypeName.get(typeMirror), "activity");


            for (Element element:elements) {
                VariableElement bindViewElement = (VariableElement) element;
                String bindViewFiledName = bindViewElement.getSimpleName().toString();
                String bindViewFiledClassType = bindViewElement.asType().toString();

                BindView bindView = element.getAnnotation(BindView.class);
                int id = bindView.value();
                note(String.format("%s %s = %d", bindViewFiledClassType, bindViewFiledName, id));
                methodBuilder.addStatement("activity." +  bindViewFiledName + " = ("+bindViewFiledClassType+")activity.findViewById("+ id +")");
            }
            MethodSpec bindMethod = methodBuilder.build();

            TypeSpec bindToolClass = TypeSpec.classBuilder(elements.get(0).getEnclosingElement().getSimpleName() + "$BindView")
                    .superclass(TypeName.get(typeMirror))
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(bindMethod)
                    .build();

            JavaFile javaFile = JavaFile.builder(pkName, bindToolClass)
                    .addFileComment("this code is auto generate")
                    .build();
            try {
                javaFile.writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        return false;
    }
    private void note(String msg) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, "-------------------------------------"+msg);
    }

    private void note(String format, Object... args) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, String.format(format, args));
    }

}
